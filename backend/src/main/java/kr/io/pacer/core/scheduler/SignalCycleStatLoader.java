package kr.io.pacer.core.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.sql.Connection;

@Slf4j
@Component
@Order(1) // BusStopSyncScheduler(ApplicationRunner)보다 먼저 실행
@RequiredArgsConstructor
public class SignalCycleStatLoader implements ApplicationRunner {

    private static final String COPY_CMD =
            "COPY signal_cycle_stat (direction, itst_id, signal_type, state, " +
            "avg_seconds, calculated_at, max_seconds, min_seconds, occurrences) FROM STDIN";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM signal_cycle_stat", Integer.class);
        if (count != null && count > 0) {
            log.info("[SignalCycleStat] 초기 적재 스킵 - 이미 {}건 존재", count);
            return;
        }

        ClassPathResource resource = new ClassPathResource("db/data/signal_cycle_stat.sql");
        if (!resource.exists()) {
            log.warn("[SignalCycleStat] 데이터 파일 없음 (db/data/signal_cycle_stat.sql)");
            return;
        }

        log.info("[SignalCycleStat] 초기 데이터 적재 시작");
        StringBuilder data = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream()))) {
            String line;
            boolean copying = false;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("COPY ")) { copying = true; continue; }
                if (!copying) continue;
                if (line.equals("\\.")) break;
                data.append(line).append("\n");
            }
        }

        try (Connection conn = dataSource.getConnection()) {
            CopyManager cm = conn.unwrap(PGConnection.class).getCopyAPI();
            long rows = cm.copyIn(COPY_CMD, new StringReader(data.toString()));
            log.info("[SignalCycleStat] 초기 데이터 적재 완료 | {}건", rows);
        }
    }
}
