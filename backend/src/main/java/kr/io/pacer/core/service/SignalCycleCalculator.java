package kr.io.pacer.core.service;

import kr.io.pacer.core.domain.SignalHistory;
import kr.io.pacer.core.repository.jpa.SignalCycleJpaRepository;
import kr.io.pacer.core.repository.jpa.SignalHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SignalCycleCalculator {

    private static final int HISTORY_LIMIT = 50;

    private final SignalHistoryRepository signalHistoryRepository;
    private final SignalCycleJpaRepository signalCycleRepository;

    public void calculate(Integer itstId, String direction) {
        List<SignalHistory> history = signalHistoryRepository
                .findByItstIdAndDirectionOrderByRecordedAtDesc(
                        itstId, direction, PageRequest.of(0, HISTORY_LIMIT))
                .stream()
                .sorted(Comparator.comparing(SignalHistory::getRecordedAt))
                .toList();

        if (history.size() < 2) return;

        Integer greenDuration = calcAverageDuration(history, true);
        Integer redDuration   = calcAverageDuration(history, false);

        if (greenDuration == null && redDuration == null) return;

        signalCycleRepository.upsert(itstId, direction, greenDuration, redDuration);
        log.debug("[SignalCycle] itstId={} dir={} green={}s red={}s", itstId, direction, greenDuration, redDuration);
    }

    private Integer calcAverageDuration(List<SignalHistory> history, boolean forGreen) {
        List<Integer> durations = new ArrayList<>();
        int runStart = -1;

        for (int i = 0; i < history.size(); i++) {
            boolean match = forGreen
                    ? isGreen(history.get(i).getStatNm())
                    : isRed(history.get(i).getStatNm());

            if (match) {
                if (runStart < 0) runStart = i;
            } else {
                if (runStart >= 0) {
                    // 직전 run 완료: runStart ~ i-1
                    int secs = (int) Duration.between(
                            history.get(runStart).getRecordedAt(),
                            history.get(i).getRecordedAt()
                    ).getSeconds();
                    if (secs > 0) durations.add(secs);
                    runStart = -1;
                }
            }
        }

        if (durations.isEmpty()) return null;
        return (int) durations.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    private boolean isGreen(String statNm) {
        if (statNm == null) return false;
        String upper = statNm.toUpperCase();
        return upper.contains("GREEN") || upper.contains("녹색");
    }

    private boolean isRed(String statNm) {
        if (statNm == null) return false;
        String upper = statNm.toUpperCase();
        return upper.contains("RED") || upper.contains("적색");
    }
}
