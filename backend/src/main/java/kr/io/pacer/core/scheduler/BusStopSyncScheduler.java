package kr.io.pacer.core.scheduler;

import kr.io.pacer.core.service.BusStopSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BusStopSyncScheduler implements ApplicationRunner {

    private final BusStopSyncService busStopSyncService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${bus-stop.city-codes}")
    private String cityCodesRaw;

    @Override
    public void run(ApplicationArguments args) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bus_stops", Integer.class);
        if (count != null && count > 0) {
            log.info("[BusStop] 초기 적재 스킵 - 이미 {}건 존재", count);
            return;
        }
        log.info("[BusStop] 초기 적재 시작");
        sync();
    }

    @Scheduled(cron = "0 0 3 1 * *") // 매월 1일 오전 3시
    public void sync() {
        List<Integer> cityCodes = Arrays.stream(cityCodesRaw.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .toList();

        log.info("[BusStop] 동기화 시작 | cityCodes={}", cityCodes);
        try {
            int total = busStopSyncService.sync(cityCodes);
            log.info("[BusStop] 동기화 완료 | 총 {}건 upsert", total);
        } catch (Exception e) {
            log.error("[BusStop] 동기화 실패 | error={}", e.getMessage(), e);
        }
    }
}
