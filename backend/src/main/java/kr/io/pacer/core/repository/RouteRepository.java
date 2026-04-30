package kr.io.pacer.core.repository;

import kr.io.pacer.core.dto.internal.RouteSegment;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
@RequiredArgsConstructor
public class RouteRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<RouteSegment> findRoute(long startNode, long endNode,
                                        double userSpeed, double startEpochSec) {
        String sql = """
            SELECT r.seq,
                   r.edge,
                   r.node                                    AS target_node,
                   r.cost,
                   ST_AsText(e.geom)                         AS geom_wkt,
                   ST_Length(e.geom::geography)              AS length_m,
                   ST_Y(ST_EndPoint(e.geom))                 AS end_lat,
                   ST_X(ST_EndPoint(e.geom))                 AS end_lng,
                   SUM(r.cost) OVER (ORDER BY r.seq)         AS cumulative_sec
            FROM pgr_aStar(
              format($fmt$
                SELECT e.id, e.source, e.target,
                       (ST_Length(e.geom::geography) / %s)
                         + signal_wait_seconds(e.target,
                               %s + (ST_Length(e.geom::geography) / %s))
                       AS cost,
                       (ST_Length(e.geom::geography) / %s)
                         + signal_wait_seconds(e.source, %s)
                       AS reverse_cost,
                       ST_X(ST_StartPoint(e.geom)) AS x1,
                       ST_Y(ST_StartPoint(e.geom)) AS y1,
                       ST_X(ST_EndPoint(e.geom))   AS x2,
                       ST_Y(ST_EndPoint(e.geom))   AS y2
                FROM road_edges e
                WHERE e.is_pedestrian = true
              $fmt$, %s, %s, %s, %s, %s),
              %d, %d, directed := false
            ) r
            JOIN road_edges e ON e.id = r.edge
            WHERE r.edge != -1
            ORDER BY r.seq
            """.formatted(
                userSpeed, startEpochSec, userSpeed,
                userSpeed, startEpochSec,
                userSpeed, startEpochSec, userSpeed, userSpeed, startEpochSec,
                startNode, endNode
        );

        return jdbcTemplate.query(sql, (rs, rowNum) -> RouteSegment.builder()
                .seq(rs.getInt("seq"))
                .edgeId(rs.getLong("edge"))
                .targetNodeId(rs.getLong("target_node"))
                .costSeconds(rs.getDouble("cost"))
                .geomWkt(rs.getString("geom_wkt"))
                .lengthMeters(rs.getDouble("length_m"))
                .endLat(rs.getDouble("end_lat"))
                .endLng(rs.getDouble("end_lng"))
                .cumulativeSeconds(rs.getDouble("cumulative_sec"))
                .build());
    }

    public long findNearestNode(double lat, double lng) {
        String sql = """
            SELECT id FROM road_nodes
            WHERE component IS NOT DISTINCT FROM (
                SELECT component FROM road_nodes
                WHERE component IS NOT NULL
                GROUP BY component ORDER BY COUNT(*) DESC LIMIT 1
            )
            ORDER BY geom <-> ST_SetSRID(ST_Point(?, ?), 4326)
            LIMIT 1
            """;
        Long result = jdbcTemplate.queryForObject(sql, Long.class, lng, lat);
        if (result == null) {
            throw new kr.io.pacer.core.exception.RouteNotFoundException("근처에 경로 데이터가 없습니다. 요청 좌표를 확인해 주세요.");
        }
        return result;
    }
}