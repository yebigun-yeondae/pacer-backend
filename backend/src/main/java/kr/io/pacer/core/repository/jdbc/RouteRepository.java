package kr.io.pacer.core.repository.jdbc;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class RouteRepository {

    private static final double CROSSWALK_SEARCH_RADIUS_M = 20.0;

    private final JdbcTemplate jdbcTemplate;

    public List<IntersectionInfo> findIntersectionsByRouteWkt(String lineStringWkt) {
        String sql = """
                SELECT itst_id, name,
                       ST_Y(geom) AS lat,
                       ST_X(geom) AS lng,
                       ST_LineLocatePoint(ST_GeomFromText(?, 4326), geom) AS fraction
                FROM intersections
                WHERE ST_DWithin(
                    ST_GeomFromText(?, 4326)::geography,
                    geom::geography,
                    100
                )
                ORDER BY fraction
                """;
        return jdbcTemplate.query(sql,
                ps -> {
                    ps.setString(1, lineStringWkt);
                    ps.setString(2, lineStringWkt);
                },
                (rs, rowNum) -> new IntersectionInfo(
                        rs.getInt("itst_id"),
                        rs.getString("name"),
                        rs.getDouble("lat"),
                        rs.getDouble("lng"),
                        rs.getDouble("fraction")));
    }

    public List<CrosswalkInfo> findCrosswalksByRouteWkt(String lineStringWkt, double totalDistanceM) {
        String sql = """
                WITH route AS (
                    SELECT ST_GeomFromText(?, 4326) AS geom
                ),
                ranked AS (
                    SELECT DISTINCT ON (c.osm_way_id)
                           c.osm_way_id,
                           c.nearest_itst_id,
                           ST_Y(c.center_geom) AS lat,
                           ST_X(c.center_geom) AS lng,
                           ST_LineLocatePoint(r.geom, c.center_geom) AS fraction
                    FROM crosswalks c
                    CROSS JOIN route r
                    WHERE ST_DWithin(
                        r.geom::geography,
                        c.geom::geography,
                        ?
                    )
                    ORDER BY c.osm_way_id, ST_Distance(r.geom::geography, c.geom::geography)
                )
                SELECT osm_way_id,
                       nearest_itst_id,
                       lat,
                       lng,
                       fraction,
                       fraction * ? AS distance_from_start
                FROM ranked
                ORDER BY fraction
                """;
        return jdbcTemplate.query(sql,
                ps -> {
                    ps.setString(1, lineStringWkt);
                    ps.setDouble(2, CROSSWALK_SEARCH_RADIUS_M);
                    ps.setDouble(3, totalDistanceM);
                },
                (rs, rowNum) -> new CrosswalkInfo(
                        rs.getLong("osm_way_id"),
                        rs.getObject("nearest_itst_id", Integer.class),
                        rs.getDouble("lat"),
                        rs.getDouble("lng"),
                        rs.getDouble("fraction"),
                        rs.getDouble("distance_from_start")));
    }

    public record IntersectionInfo(int itstId, String name, double lat, double lng, double fraction) {}

    public record CrosswalkInfo(
            long crosswalkId,
            Integer itstId,
            double lat,
            double lng,
            double fraction,
            double distanceFromStart
    ) {}
}
