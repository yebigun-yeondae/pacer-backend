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
    private static final double DUPLICATE_SIGNAL_EVENT_DISTANCE_M = 30.0;

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
        List<ResolvedCrosswalkSignal> selectedSignals = resolveCrosswalkSignals(
                selected.crosswalks(), spatMap, stateMap, cycleMap);

        List<RouteResponse.SignalCheckpoint> checkpoints = buildCheckpoints(selectedSignals, selected.totalTimeSec());
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

            List<ResolvedCrosswalkSignal> crosswalkSignals = resolveCrosswalkSignals(
                    route.crosswalks(), spatMap, stateMap, cycleMap);
            List<AiRouteRequest.Crosswalk> crosswalks = crosswalkSignals.stream()
                    .map(crosswalkSignal -> {
                        CrosswalkInfo crosswalk = crosswalkSignal.crosswalk();
                        AiRouteRequest.Signal signal = buildAiSignal(
                                crosswalkSignal.remainingSeconds(), crosswalkSignal.cycleSeconds());
                        log.debug("[AI-CROSSWALK] routeId={} crosswalkId={} intersectionId={} rawDirection={} "
                                        + "resolvedDirection={} distance={} remaining={} cycle={} signal={}",
                                routeId,
                                crosswalk.crosswalkId(),
                                crosswalk.itstId(),
                                crosswalk.signalDirection(),
                                crosswalkSignal.signalDirection(),
                                crosswalk.distanceFromStart(),
                                crosswalkSignal.remainingSeconds(),
                                crosswalkSignal.cycleSeconds(),
                                describeSignal(signal));

                        return AiRouteRequest.Crosswalk.builder()
                                .crosswalkId(String.valueOf(crosswalk.crosswalkId()))
                                .intersectionId(crosswalk.itstId())
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

    private AiRouteRequest.Signal buildAiSignal(Double remainingSeconds, Double cycleSeconds) {
        String phase = remainingSeconds == null ? null : "green";
        if (phase == null || remainingSeconds == null || cycleSeconds == null) return null;
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

    private String describeSignal(AiRouteRequest.Signal signal) {
        if (signal == null) return "null";
        return "phase=" + signal.getPhase()
                + ", remaining=" + signal.getRemainingSeconds()
                + ", cycle=" + signal.getCycleSeconds();
    }

    private List<ResolvedCrosswalkSignal> resolveCrosswalkSignals(
            List<CrosswalkInfo> crosswalks,
            Map<Integer, SpatResponse> spatMap,
            Map<Integer, SpatStateResponse> stateMap,
            Map<Integer, Map<String, SignalCycle>> cycleMap) {

        List<ResolvedCrosswalkSignal> signals = crosswalks.stream()
                .map(crosswalk -> resolveCrosswalkSignal(crosswalk, spatMap, stateMap, cycleMap))
                .toList();
        return deduplicateSignalEvents(signals);
    }

    private ResolvedCrosswalkSignal resolveCrosswalkSignal(
            CrosswalkInfo crosswalk,
            Map<Integer, SpatResponse> spatMap,
            Map<Integer, SpatStateResponse> stateMap,
            Map<Integer, Map<String, SignalCycle>> cycleMap) {
        Integer itstId = crosswalk.itstId();
        SpatResponse spat = itstId == null ? null : spatMap.get(itstId);
        SpatStateResponse state = itstId == null ? null : stateMap.get(itstId);
        Map<String, SignalCycle> cycles = itstId == null ? null : cycleMap.get(itstId);
        String signalDirection = resolveCheckpointSignalDirection(
                spat, state, signalDirectionCandidates(crosswalk.signalDirection()));
        Double remainingSeconds = resolveRemaining(spat, signalDirection);
        Double cycleSeconds = resolveCycle(cycles, signalDirection);
        SignalState signalState = resolveSignalState(state, spat, signalDirection);
        return new ResolvedCrosswalkSignal(
                crosswalk, signalDirection, remainingSeconds, cycleSeconds, signalState);
    }

    private List<ResolvedCrosswalkSignal> deduplicateSignalEvents(List<ResolvedCrosswalkSignal> signals) {
        List<ResolvedCrosswalkSignal> deduplicated = new ArrayList<>();
        for (ResolvedCrosswalkSignal signal : signals) {
            if (!deduplicated.isEmpty() && isDuplicateSignalEvent(deduplicated.get(deduplicated.size() - 1), signal)) {
                continue;
            }
            deduplicated.add(signal);
        }
        return deduplicated;
    }

    private boolean isDuplicateSignalEvent(ResolvedCrosswalkSignal previous, ResolvedCrosswalkSignal current) {
        Integer previousItstId = previous.crosswalk().itstId();
        Integer currentItstId = current.crosswalk().itstId();
        String previousDirection = normalizeDirection(previous.signalDirection());
        String currentDirection = normalizeDirection(current.signalDirection());
        if (previousItstId == null || currentItstId == null) return false;
        if (previousDirection.isBlank() || currentDirection.isBlank()) return false;
        if (!previousItstId.equals(currentItstId)) return false;
        if (!previousDirection.equals(currentDirection)) return false;
        double distanceDelta = Math.abs(previous.crosswalk().distanceFromStart() - current.crosswalk().distanceFromStart());
        return distanceDelta <= DUPLICATE_SIGNAL_EVENT_DISTANCE_M;
    }

    private List<RouteResponse.SignalCheckpoint> buildCheckpoints(
            List<ResolvedCrosswalkSignal> crosswalkSignals,
            int totalTimeSec) {

        return IntStream.range(0, crosswalkSignals.size())
                .mapToObj(idx -> {
                    ResolvedCrosswalkSignal crosswalkSignal = crosswalkSignals.get(idx);
                    CrosswalkInfo crosswalk = crosswalkSignal.crosswalk();
                    int etaSec = (int) (crosswalk.fraction() * totalTimeSec);
                    return RouteResponse.SignalCheckpoint.builder()
                            .order(idx + 1)
                            .crosswalkId(String.valueOf(crosswalk.crosswalkId()))
                            .intersectionId(crosswalk.itstId())
                            .lat(crosswalk.lat())
                            .lng(crosswalk.lng())
                            .etaFromStartSeconds(etaSec)
                            .signalDirection(crosswalkSignal.signalDirection())
                            .remainingSeconds(crosswalkSignal.remainingSeconds())
                            .signalState(crosswalkSignal.signalState())
                            .build();
                })
                .toList();
    }

    private String resolveCheckpointSignalDirection(
            SpatResponse spat,
            SpatStateResponse state,
            List<String> directionCandidates) {
        if (directionCandidates.size() == 1) return directionCandidates.get(0);
        List<String> usableDirections = directionCandidates.stream()
                .filter(direction -> resolveRemaining(spat, direction) != null
                        || resolveDirectionStatus(state, direction) != null)
                .toList();
        return usableDirections.size() == 1 ? usableDirections.get(0) : null;
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

    private SignalState resolveSignalState(SpatStateResponse state, SpatResponse spat, String signalDirection) {
        String status = resolveDirectionStatus(state, signalDirection);
        if (status != null) {
            if (status.contains("녹색")) return SignalState.GREEN;
            if (status.contains("적색")) return SignalState.RED;
        }
        if (spat == null || signalDirection == null) return SignalState.UNKNOWN;
        return resolveRemaining(spat, signalDirection) == null ? SignalState.RED : SignalState.GREEN;
    }

    private String resolveDirectionStatus(SpatStateResponse state, String signalDirection) {
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
            default -> null;
        };
    }

    private boolean isValidPdsg(Double value) {
        return value != null && value < 36001.0 && value > 0;
    }

    private record ResolvedCrosswalkSignal(
            CrosswalkInfo crosswalk,
            String signalDirection,
            Double remainingSeconds,
            Double cycleSeconds,
            SignalState signalState
    ) {}
}
