package kr.io.pacer.core.service;

import kr.io.pacer.core.client.CitsSpbiClient;
import kr.io.pacer.core.client.CitsSpatClient;
import kr.io.pacer.core.domain.Intersection;
import kr.io.pacer.core.domain.SignalHistory;
import kr.io.pacer.core.dto.external.SpbiResponse;
import kr.io.pacer.core.repository.jpa.IntersectionRepository;
import kr.io.pacer.core.repository.jpa.SignalHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignalPollingService {

    private static final List<String> DIRECTIONS = List.of("N", "E", "S", "W", "NE", "SE", "SW", "NW");

    private final IntersectionRepository  intersectionRepository;
    private final CitsSpbiClient          citsSpbiClient;
    private final CitsSpatClient          citsSpatClient;
    private final SignalHistoryRepository signalHistoryRepository;
    private final SignalCycleCalculator   signalCycleCalculator;

    @Scheduled(fixedDelay = 5000)
    public void poll() {
        List<Integer> itstIds = intersectionRepository.findAll().stream()
                .map(Intersection::getItstId)
                .toList();

        if (itstIds.isEmpty()) return;

        CompletableFuture<Map<Integer, SpbiResponse>> spbiFuture =
                CompletableFuture.supplyAsync(() -> citsSpbiClient.fetchAll(itstIds));
        CompletableFuture<?> spatFuture =
                CompletableFuture.supplyAsync(() -> citsSpatClient.fetchAll(itstIds));

        CompletableFuture.allOf(spbiFuture, spatFuture).join();

        Map<Integer, SpbiResponse> spbiMap = spbiFuture.join();

        LocalDateTime now = LocalDateTime.now();
        List<SignalHistory> toSave = new ArrayList<>();
        Set<String> updatedKeys = new LinkedHashSet<>();  // "itstId:direction" 중복 방지

        for (Integer itstId : itstIds) {
            SpbiResponse spbi = spbiMap.get(itstId);
            if (spbi == null) continue;

            for (String dir : DIRECTIONS) {
                String statNm = resolveStatNm(spbi, dir);
                if (statNm == null) continue;

                toSave.add(SignalHistory.of(itstId, dir, statNm, now));
                updatedKeys.add(itstId + ":" + dir);
            }
        }

        if (toSave.isEmpty()) return;

        signalHistoryRepository.saveAll(toSave);
        log.debug("[SignalPolling] saved {} records", toSave.size());

        for (String key : updatedKeys) {
            String[] parts = key.split(":");
            signalCycleCalculator.calculate(Integer.valueOf(parts[0]), parts[1]);
        }
    }

    private String resolveStatNm(SpbiResponse spbi, String direction) {
        return switch (direction) {
            case "N"  -> spbi.getNtPdsgStatNm();
            case "E"  -> spbi.getEtPdsgStatNm();
            case "S"  -> spbi.getStPdsgStatNm();
            case "W"  -> spbi.getWtPdsgStatNm();
            case "NE" -> spbi.getNePdsgStatNm();
            case "SE" -> spbi.getSePdsgStatNm();
            case "SW" -> spbi.getSwPdsgStatNm();
            case "NW" -> spbi.getNwPdsgStatNm();
            default   -> null;
        };
    }
}
