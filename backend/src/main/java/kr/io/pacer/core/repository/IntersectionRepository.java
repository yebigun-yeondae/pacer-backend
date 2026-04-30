package kr.io.pacer.core.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class IntersectionRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<Integer> findItstIdsByRouteWkt(String lineStringWkt) {
        String sql = """
                SELECT itst_id
                FROM intersections
                WHERE ST_DWithin(
                    geom::geography,
                    ST_SetSRID(ST_GeomFromText(?), 4326)::geography,
                    100
                )
                ORDER BY ST_LineLocatePoint(ST_SetSRID(ST_GeomFromText(?), 4326), geom)
                """;
        return jdbcTemplate.queryForList(sql, Integer.class, lineStringWkt, lineStringWkt);
    }
}
