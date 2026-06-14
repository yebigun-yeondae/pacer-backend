package kr.io.pacer.core.repository.jdbc;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class RouteRepository {

    private static final double CROSSWALK_CENTER_SEARCH_RADIUS_M = 5.0;

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
                mapped AS (
                    SELECT DISTINCT ON (c.osm_way_id)
                           c.osm_way_id,
                           c.nearest_itst_id,
                           i.name AS intersection_name,
                           ST_Y(c.center_geom) AS lat,
                           ST_X(c.center_geom) AS lng,
                           ST_LineLocatePoint(r.geom, c.center_geom) AS fraction,
                           CASE ((floor((degrees(ST_Azimuth(i.geom::geography, c.center_geom::geography)) + 22.5) / 45)::int + 2) % 8)
                               WHEN 0 THEN 'nt'
                               WHEN 1 THEN 'ne'
                               WHEN 2 THEN 'et'
                               WHEN 3 THEN 'se'
                               WHEN 4 THEN 'st'
                               WHEN 5 THEN 'sw'
                               WHEN 6 THEN 'wt'
                               WHEN 7 THEN 'nw'
                           END AS raw_signal_direction,
                           CASE
                               WHEN degrees(ST_Azimuth(ST_StartPoint(c.geom)::geography, ST_EndPoint(c.geom)::geography)) >= 45
                                    AND degrees(ST_Azimuth(ST_StartPoint(c.geom)::geography, ST_EndPoint(c.geom)::geography)) < 135 THEN TRUE
                               WHEN degrees(ST_Azimuth(ST_StartPoint(c.geom)::geography, ST_EndPoint(c.geom)::geography)) >= 225
                                    AND degrees(ST_Azimuth(ST_StartPoint(c.geom)::geography, ST_EndPoint(c.geom)::geography)) < 315 THEN TRUE
                               ELSE FALSE
                           END AS horizontal_crossing
                    FROM crosswalks c
                    CROSS JOIN route r
                    JOIN intersections i ON i.itst_id = c.nearest_itst_id
                    WHERE ST_DWithin(
                        r.geom::geography,
                        c.center_geom::geography,
                        ?
                    )
                    ORDER BY c.osm_way_id, ST_Distance(r.geom::geography, c.center_geom::geography)
                ),
                ranked AS (
                    SELECT osm_way_id,
                           nearest_itst_id,
                           intersection_name,
                           lat,
                           lng,
                           fraction,
                           CASE raw_signal_direction
                               WHEN 'ne' THEN CASE WHEN horizontal_crossing THEN 'et' ELSE 'nt' END
                               WHEN 'se' THEN CASE WHEN horizontal_crossing THEN 'et' ELSE 'st' END
                               WHEN 'sw' THEN CASE WHEN horizontal_crossing THEN 'wt' ELSE 'st' END
                               WHEN 'nw' THEN CASE WHEN horizontal_crossing THEN 'wt' ELSE 'nt' END
                               ELSE raw_signal_direction
                           END AS signal_direction
                    FROM mapped
                )
                SELECT osm_way_id,
                       nearest_itst_id,
                       intersection_name,
                       lat,
                       lng,
                       fraction,
                       signal_direction,
                       fraction * ? AS distance_from_start
                FROM ranked
                ORDER BY fraction
                """;
        return jdbcTemplate.query(sql,
                ps -> {
                    ps.setString(1, lineStringWkt);
                    ps.setDouble(2, CROSSWALK_CENTER_SEARCH_RADIUS_M);
                    ps.setDouble(3, totalDistanceM);
                },
                (rs, rowNum) -> new CrosswalkInfo(
                        rs.getLong("osm_way_id"),
                        rs.getObject("nearest_itst_id", Integer.class),
                        rs.getString("intersection_name"),
                        rs.getDouble("lat"),
                        rs.getDouble("lng"),
                        rs.getDouble("fraction"),
                        rs.getString("signal_direction"),
                        rs.getDouble("distance_from_start")));
    }

    public record IntersectionInfo(int itstId, String name, double lat, double lng, double fraction) {}

    public record CrosswalkInfo(
            long crosswalkId,
            Integer itstId,
            String intersectionName,
            double lat,
            double lng,
            double fraction,
            String signalDirection,
            double distanceFromStart
    ) {}
}
