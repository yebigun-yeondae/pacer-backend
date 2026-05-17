package kr.io.pacer.core.repository.jdbc;

import kr.io.pacer.core.dto.response.RouteResponse.SignalCycle;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class SignalCycleRepository {

    private final JdbcTemplate jdbcTemplate;

    public Map<Integer, Map<String, SignalCycle>> findByItstIds(List<Integer> itstIds) {
        if (itstIds.isEmpty()) return Map.of();

        String placeholders = String.join(",", Collections.nCopies(itstIds.size(), "?"));
        String sql = """
                SELECT itst_id, direction,
                       NULLIF(ROUND(CAST(SUM(CASE WHEN state != 'stop-And-Remain' THEN max_seconds ELSE 0 END) AS numeric), 2), 0) AS green_avg_sec,
                       ROUND(CAST(MAX(CASE WHEN state = 'stop-And-Remain' THEN max_seconds END) AS numeric), 2) AS red_avg_sec
                FROM signal_cycle_stat
                WHERE itst_id IN (%s)
                  AND signal_type = 'pdsg'
                GROUP BY itst_id, direction
                """.formatted(placeholders);

        Object[] params = itstIds.stream().map(String::valueOf).toArray();

        Map<Integer, Map<String, SignalCycle>> result = new HashMap<>();
        jdbcTemplate.query(sql, params, rs -> {
            int itstId = Integer.parseInt(rs.getString("itst_id"));
            String direction = rs.getString("direction");
            Double greenMaxSec = rs.getObject("green_avg_sec", Double.class);
            Double redMaxSec   = rs.getObject("red_avg_sec",   Double.class);
            result.computeIfAbsent(itstId, k -> new HashMap<>())
                  .put(direction, SignalCycle.builder()
                          .redMaxSec(redMaxSec)
                          .greenMaxSec(greenMaxSec)
                          .build());
        });

        return result;
    }
}
