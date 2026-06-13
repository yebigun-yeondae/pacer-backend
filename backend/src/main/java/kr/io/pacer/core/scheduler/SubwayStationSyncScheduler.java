package kr.io.pacer.core.scheduler;

import kr.io.pacer.core.service.SubwayStationSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubwayStationSyncScheduler implements ApplicationRunner {

    private final SubwayStationSyncService subwayStationSyncService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM subway_stations", Integer.class);
        if (count != null && count > 0) {
            log.info("[Subway] 초기 적재 스킵 - 이미 {}개 역 존재", count);
            return;
        }
        log.info("[Subway] 역사 초기 적재 시작");
        sync();
    }

    @Scheduled(cron = "0 0 4 1 * *") // 매월 1일 오전 4시
    public void sync() {
        log.info("[Subway] 역사 동기화 시작");
        try {
            int total = subwayStationSyncService.sync();
            log.info("[Subway] 역사 동기화 완료 | 총 {}개역 upsert", total);
        } catch (Exception e) {
            log.error("[Subway] 역사 동기화 실패 | error={}", e.getMessage(), e);
        }
    }
}
