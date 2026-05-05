package kr.io.pacer.core.repository.jdbc;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class RouteRepository {

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

    public record IntersectionInfo(int itstId, String name, double lat, double lng, double fraction) {}
}
