package kr.io.pacer.core.service;

import kr.io.pacer.core.client.AiRouteClient;
import kr.io.pacer.core.client.CitsSpatClient;
import kr.io.pacer.core.client.CitsSpatStateClient;
import kr.io.pacer.core.domain.RouteHistory;
import kr.io.pacer.core.domain.User;
import kr.io.pacer.core.domain.enums.SignalState;
import kr.io.pacer.core.dto.ai.AiRouteRequest;
import kr.io.pacer.core.dto.ai.AiRouteResponse;
import kr.io.pacer.core.dto.external.SpatResponse;
import kr.io.pacer.core.dto.external.SpatStateResponse;
import kr.io.pacer.core.dto.request.RouteRequest;
import kr.io.pacer.core.dto.response.RouteHistoryResponse;
import kr.io.pacer.core.dto.response.RouteResponse;
import kr.io.pacer.core.dto.response.RouteResponse.SignalCycle;
import kr.io.pacer.core.repository.jdbc.RouteRepository.CrosswalkInfo;
import kr.io.pacer.core.repository.jdbc.RouteRepository.IntersectionInfo;
import kr.io.pacer.core.repository.jdbc.SignalCycleRepository;
import kr.io.pacer.core.repository.jpa.PedestrianProfileRepository;
import kr.io.pacer.core.repository.jpa.RouteHistoryRepository;
import kr.io.pacer.core.repository.jpa.UserRepository;
import kr.io.pacer.core.service.RouteGeometryService.CachedRoute;
import kr.io.pacer.core.util.PolylineEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

    private static final double DEFAULT_SPEED_STD = 0.2;

    private final RouteGeometryService routeGeometryService;
    private final CitsSpatClient citsSpatClient;
    private final CitsSpatStateClient citsSpatStateClient;
    private final SignalCycleRepository signalCycleRepository;
    private final AiRouteClient aiRouteClient;
    private final PolylineEncoder polylineEncoder;
    private final PedestrianProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final RouteHistoryRepository historyRepository;
    private final FavoritePlaceService favoritePlaceService;

    @Transactional(readOnly = true)
    public List<RouteHistoryResponse> getHistory(UUID userId, Pageable pageable) {
        return historyRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .stream()
                .map(RouteHistoryResponse::from)
                .toList();
    }

    public RouteResponse findRoute(RouteRequest req, UUID userId) {
        log.info("[Route] 경로 탐색 시작 | userId={} origin=({},{}) dest=({},{})",
                userId,
                req.getOrigin().getLat(), req.getOrigin().getLng(),
                req.getDestination().getLat(), req.getDestination().getLng());

        List<CachedRoute> candidates = routeGeometryService.fetchAll(req, userId);

        List<Integer> itstIds = candidates.stream()
                .flatMap(r -> Stream.concat(
                        r.intersections().stream().map(IntersectionInfo::itstId),
                        r.crosswalks().stream().map(CrosswalkInfo::itstId)))
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        CompletableFuture<Map<Integer, SpatResponse>> spatFuture = itstIds.isEmpty()
                ? CompletableFuture.completedFuture(Map.of())
                : CompletableFuture.supplyAsync(() -> citsSpatClient.fetchAll(itstIds));
        CompletableFuture<Map<Integer, SpatStateResponse>> stateFuture = itstIds.isEmpty()
                ? CompletableFuture.completedFuture(Map.of())
                : CompletableFuture.supplyAsync(() -> citsSpatStateClient.fetchAll(itstIds));
        CompletableFuture<Map<Integer, Map<String, SignalCycle>>> cycleFuture = itstIds.isEmpty()
                ? CompletableFuture.completedFuture(Map.of())
                : CompletableFuture.supplyAsync(() -> signalCycleRepository.findByItstIds(itstIds));

        CompletableFuture.allOf(spatFuture, stateFuture, cycleFuture).join();

        Map<Integer, SpatResponse> spatMap = spatFuture.join();
        Map<Integer, SpatStateResponse> stateMap = stateFuture.join();
        Map<Integer, Map<String, SignalCycle>> cycleMap = cycleFuture.join();

        itstIds.stream()
                .filter(id -> !spatMap.containsKey(id))
                .forEach(id -> log.warn("[CITS] 신호 데이터 누락 itstId={}", id));

        CachedRoute selected = selectRouteViaAi(userId, candidates, spatMap, stateMap, cycleMap);

        List<RouteResponse.SignalCheckpoint> checkpoints = buildCheckpoints(selected.intersections(), spatMap, stateMap, selected.totalTimeSec());
        List<RouteResponse.IntersectionSignal> intersectionSignals = buildIntersectionSignals(selected.intersections(), spatMap, stateMap, cycleMap);

        int signalStops = (int) checkpoints.stream()
                .filter(c -> c.getSignalState() == SignalState.RED).count();

        RouteResponse response = RouteResponse.builder()
                .polyline(selected.polyline())
                .totalTimeSeconds(selected.totalTimeSec())
                .totalDistanceMeters(selected.totalDistanceM())
                .signalCheckpoints(checkpoints)
                .intersectionSignals(intersectionSignals)
                .build();

        User user = userRepository.getReferenceById(userId);
        historyRepository.save(
                RouteHistory.of(user, req, selected.polyline(), selected.totalTimeSec(), selected.totalDistanceM(), signalStops));
        profileRepository.incrementRouteStats(userId, selected.totalDistanceM());
        favoritePlaceService.incrementVisitIfNearby(
                userId, req.getDestination().getLat(), req.getDestination().getLng());

        log.info("[Route] 경로 탐색 완료 | userId={} distance={}m time={}s stops={} intersections={}",
                userId, (int) selected.totalDistanceM(), selected.totalTimeSec(), signalStops, intersectionSignals.size());
        return response;
    }

    private CachedRoute selectRouteViaAi(UUID userId, List<CachedRoute> candidates,
                                          Map<Integer, SpatResponse> spatMap,
                                          Map<Integer, SpatStateResponse> stateMap,
                                          Map<Integer, Map<String, SignalCycle>> cycleMap) {
        try {
            AiRouteRequest aiRequest = buildAiRequest(userId, candidates, spatMap, stateMap, cycleMap);
            AiRouteResponse aiResponse = aiRouteClient.selectRoute(aiRequest);

            String optimalId = aiResponse.getOptimalRouteId(); // "route_001"
            int index = Integer.parseInt(optimalId.replace("route_", "")) - 1;
            if (index < 0 || index >= candidates.size()) {
                log.warn("[AI] 유효하지 않은 경로 인덱스 | optimalId={} candidateSize={}", optimalId, candidates.size());
                return candidates.get(0);
            }
            return candidates.get(index);
        } catch (Exception e) {
            log.warn("[AI] 경로 선택 실패, 첫 번째 경로 사용: {}", e.getMessage());
            return candidates.get(0);
        }
    }

    private AiRouteRequest buildAiRequest(UUID userId, List<CachedRoute> candidates,
                                           Map<Integer, SpatResponse> spatMap,
                                           Map<Integer, SpatStateResponse> stateMap,
                                           Map<Integer, Map<String, SignalCycle>> cycleMap) {
        double avgSpeed = profileRepository.findByUserId(userId)
                .map(p -> p.getAvgSpeedMps())
                .orElse(1.4);
        int tripCount = profileRepository.findByUserId(userId)
                .map(p -> p.getTotalRoutes())
                .orElse(0);

        List<AiRouteRequest.RouteCandidate> routeCandidates = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            CachedRoute route = candidates.get(i);
            String routeId = String.format("route_%03d", i + 1);

            List<double[]> points = polylineEncoder.decode(route.polyline());
            List<AiRouteRequest.Waypoint> waypoints = points.stream()
                    .map(p -> AiRouteRequest.Waypoint.builder()
                            .lat(p[0]).lng(p[1]).elevationM(null).build())
                    .toList();

            List<AiRouteRequest.Crosswalk> crosswalks = route.crosswalks().stream()
                    .map(crosswalk -> {
                        Integer itstId = crosswalk.itstId();
                        String rawSignalDirection = crosswalk.signalDirection();
                        SpatResponse spat = itstId == null ? null : spatMap.get(itstId);
                        SpatStateResponse spatState = itstId == null ? null : stateMap.get(itstId);
                        Map<String, SignalCycle> cycles = itstId == null ? null : cycleMap.get(itstId);
                        List<String> directionCandidates = signalDirectionCandidates(rawSignalDirection);
                        String signalDirection = resolveSignalDirection(spat, cycles, directionCandidates);
                        Double remainingSeconds = resolveRemaining(spat, signalDirection);
                        Double cycleSeconds = resolveCycle(cycles, signalDirection);
                        String statNm = resolveStatNm(spatState, signalDirection);
                        AiRouteRequest.Signal signal = buildAiSignal(remainingSeconds, cycleSeconds, statNm);
                        log.debug("[AI-CROSSWALK] routeId={} crosswalkId={} intersectionId={} rawDirection={} "
                                        + "resolvedDirection={} candidates={} distance={} spat={} remaining={} "
                                        + "cycleDirections={} cycle={} signal={}",
                                routeId,
                                crosswalk.crosswalkId(),
                                itstId,
                                rawSignalDirection,
                                signalDirection,
                                directionCandidates,
                                crosswalk.distanceFromStart(),
                                spat != null,
                                remainingSeconds,
                                cycles == null ? "[]" : cycles.keySet(),
                                cycleSeconds,
                                describeSignal(signal));

                        return AiRouteRequest.Crosswalk.builder()
                                .crosswalkId(String.valueOf(crosswalk.crosswalkId()))
                                .intersectionId(itstId)
                                .distanceFromStart(crosswalk.distanceFromStart())
                                .signal(signal)
                                .build();
                    })
                    .toList();

            routeCandidates.add(AiRouteRequest.RouteCandidate.builder()
                    .routeId(routeId)
                    .waypoints(waypoints)
                    .crosswalks(crosswalks)
                    .totalDistance(route.totalDistanceM())
                    .build());
        }

        return AiRouteRequest.builder()
                .userId(userId.toString())
                .userProfile(AiRouteRequest.UserProfile.builder()
                        .avgSpeed(avgSpeed)
                        .speedStd(DEFAULT_SPEED_STD)
                        .tripCount(tripCount)
                        .build())
                .routeCandidates(routeCandidates)
                .build();
    }

    private AiRouteRequest.Signal buildAiSignal(Double remainingSeconds, Double cycleSeconds, String statNm) {
        if (remainingSeconds == null || cycleSeconds == null) return null;
        String phase = statNm != null
                ? (isGreenStatNm(statNm) ? "green" : "red")
                : "green";
        return AiRouteRequest.Signal.builder()
                .phase(phase)
                .remainingSeconds(remainingSeconds)
                .cycleSeconds(cycleSeconds)
                .build();
    }

    private Double resolveRemaining(SpatResponse spat, String signalDirection) {
        if (spat == null) return null;
        Double value = switch (normalizeDirection(signalDirection)) {
            case "nt" -> spat.getNtPdsgRmdrCs();
            case "et" -> spat.getEtPdsgRmdrCs();
            case "st" -> spat.getStPdsgRmdrCs();
            case "wt" -> spat.getWtPdsgRmdrCs();
            case "ne" -> spat.getNePdsgRmdrCs();
            case "se" -> spat.getSePdsgRmdrCs();
            case "sw" -> spat.getSwPdsgRmdrCs();
            case "nw" -> spat.getNwPdsgRmdrCs();
            default -> null;
        };
        return isValidPdsg(value) ? value : null;
    }

    private Double resolveCycle(Map<String, SignalCycle> cycles, String signalDirection) {
        if (cycles == null || cycles.isEmpty()) return null;
        String direction = normalizeDirection(signalDirection);
        if (direction.isBlank()) return null;
        SignalCycle cycle = cycles.get(direction);
        if (cycle == null) return null;
        if (cycle.getRedMaxSec() == null || cycle.getGreenMaxSec() == null) return null;
        return cycle.getRedMaxSec() + cycle.getGreenMaxSec();
    }

    private String normalizeDirection(String signalDirection) {
        return signalDirection == null ? "" : signalDirection.toLowerCase(Locale.ROOT);
    }

    private List<String> signalDirectionCandidates(String signalDirection) {
        return switch (normalizeDirection(signalDirection)) {
            case "nt", "et", "st", "wt" -> List.of(normalizeDirection(signalDirection));
            case "ne" -> List.of("nt", "et");
            case "se" -> List.of("st", "et");
            case "sw" -> List.of("st", "wt");
            case "nw" -> List.of("nt", "wt");
            default -> List.of();
        };
    }

    private String resolveSignalDirection(
            SpatResponse spat,
            Map<String, SignalCycle> cycles,
            List<String> directionCandidates) {
        List<String> usableDirections = directionCandidates.stream()
                .filter(direction -> resolveRemaining(spat, direction) != null)
                .filter(direction -> resolveCycle(cycles, direction) != null)
                .toList();
        return usableDirections.size() == 1 ? usableDirections.get(0) : null;
    }

    private String describeSignal(AiRouteRequest.Signal signal) {
        if (signal == null) return "null";
        return "phase=" + signal.getPhase()
                + ", remaining=" + signal.getRemainingSeconds()
                + ", cycle=" + signal.getCycleSeconds();
    }

    private List<RouteResponse.SignalCheckpoint> buildCheckpoints(
            List<IntersectionInfo> intersections,
            Map<Integer, SpatResponse> spatMap,
            Map<Integer, SpatStateResponse> stateMap,
            int totalTimeSec) {

        List<IntersectionInfo> filtered = intersections.stream()
                .filter(i -> spatMap.containsKey(i.itstId()) || stateMap.containsKey(i.itstId()))
                .toList();
        return IntStream.range(0, filtered.size())
                .mapToObj(idx -> {
                    IntersectionInfo i = filtered.get(idx);
                    SpatResponse spat = spatMap.get(i.itstId());
                    SpatStateResponse state = stateMap.get(i.itstId());
                    int etaSec = (int) (i.fraction() * totalTimeSec);
                    return RouteResponse.SignalCheckpoint.builder()
                            .order(idx + 1)
                            .nodeId(i.itstId())
                            .lat(i.lat())
                            .lng(i.lng())
                            .etaFromStartSeconds(etaSec)
                            .signalState(resolveSignalState(spat, state))
                            .build();
                })
                .toList();
    }

    private List<RouteResponse.IntersectionSignal> buildIntersectionSignals(
            List<IntersectionInfo> intersections,
            Map<Integer, SpatResponse> spatMap,
            Map<Integer, SpatStateResponse> stateMap,
            Map<Integer, Map<String, SignalCycle>> cycleMap) {

        return IntStream.range(0, intersections.size())
                .mapToObj(idx -> {
                    IntersectionInfo i = intersections.get(idx);
                    RouteResponse.IntersectionSignal.IntersectionSignalBuilder builder =
                            RouteResponse.IntersectionSignal.builder()
                                    .order(idx + 1)
                                    .itstId(i.itstId())
                                    .name(i.name())
                                    .lat(i.lat())
                                    .lng(i.lng());

                    SpatResponse spat = spatMap.get(i.itstId());
                    if (spat != null) {
                        builder.ntPdsgRmdrCs(spat.getNtPdsgRmdrCs())
                               .etPdsgRmdrCs(spat.getEtPdsgRmdrCs())
                               .stPdsgRmdrCs(spat.getStPdsgRmdrCs())
                               .wtPdsgRmdrCs(spat.getWtPdsgRmdrCs())
                               .nePdsgRmdrCs(spat.getNePdsgRmdrCs())
                               .sePdsgRmdrCs(spat.getSePdsgRmdrCs())
                               .swPdsgRmdrCs(spat.getSwPdsgRmdrCs())
                               .nwPdsgRmdrCs(spat.getNwPdsgRmdrCs());
                    }

                    SpatStateResponse state = stateMap.get(i.itstId());
                    if (state != null) {
                        builder.ntPdsgStatNm(state.getNtPdsgStatNm())
                               .etPdsgStatNm(state.getEtPdsgStatNm())
                               .stPdsgStatNm(state.getStPdsgStatNm())
                               .wtPdsgStatNm(state.getWtPdsgStatNm())
                               .nePdsgStatNm(state.getNePdsgStatNm())
                               .sePdsgStatNm(state.getSePdsgStatNm())
                               .swPdsgStatNm(state.getSwPdsgStatNm())
                               .nwPdsgStatNm(state.getNwPdsgStatNm());
                    }

                    Map<String, SignalCycle> cycles = cycleMap.get(i.itstId());
                    if (cycles != null) {
                        builder.signalCycles(cycles);
                    }

                    return builder.build();
                })
                .toList();
    }

    private SignalState resolveSignalState(SpatResponse spat, SpatStateResponse state) {
        if (state != null) {
            boolean hasGreen = Stream.of(
                    state.getNtPdsgStatNm(), state.getEtPdsgStatNm(),
                    state.getStPdsgStatNm(), state.getWtPdsgStatNm(),
                    state.getNePdsgStatNm(), state.getSePdsgStatNm(),
                    state.getSwPdsgStatNm(), state.getNwPdsgStatNm()
            ).anyMatch(s -> s != null && isGreenStatNm(s));
            return hasGreen ? SignalState.GREEN : SignalState.RED;
        }
        if (spat == null) return SignalState.RED;
        boolean hasValidGreen = hasAnyValidPdsg(
                spat.getNtPdsgRmdrCs(), spat.getEtPdsgRmdrCs(),
                spat.getStPdsgRmdrCs(), spat.getWtPdsgRmdrCs(),
                spat.getNePdsgRmdrCs(), spat.getSePdsgRmdrCs(),
                spat.getSwPdsgRmdrCs(), spat.getNwPdsgRmdrCs());
        return hasValidGreen ? SignalState.GREEN : SignalState.RED;
    }

    private boolean isGreenStatNm(String statNm) {
        return "permissive-Movement-Allowed".equals(statNm)
            || "protected-Movement-Allowed".equals(statNm);
    }

    private String resolveStatNm(SpatStateResponse state, String signalDirection) {
        if (state == null) return null;
        return switch (normalizeDirection(signalDirection)) {
            case "nt" -> state.getNtPdsgStatNm();
            case "et" -> state.getEtPdsgStatNm();
            case "st" -> state.getStPdsgStatNm();
            case "wt" -> state.getWtPdsgStatNm();
            case "ne" -> state.getNePdsgStatNm();
            case "se" -> state.getSePdsgStatNm();
            case "sw" -> state.getSwPdsgStatNm();
            case "nw" -> state.getNwPdsgStatNm();
            default   -> null;
        };
    }

    private boolean hasAnyValidPdsg(Double... values) {
        for (Double v : values) {
            if (isValidPdsg(v)) return true;
        }
        return false;
    }

    private boolean isValidPdsg(Double value) {
        return value != null && value < 36001.0 && value > 0;
    }
}
