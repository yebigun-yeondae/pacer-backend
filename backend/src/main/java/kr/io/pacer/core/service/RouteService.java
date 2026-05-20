package kr.io.pacer.core.service;

import kr.io.pacer.core.client.AiRouteClient;
import kr.io.pacer.core.client.CitsSpatClient;
import kr.io.pacer.core.client.CitsSpatStateClient;
import kr.io.pacer.core.domain.RouteHistory;
import kr.io.pacer.core.domain.User;
import kr.io.pacer.core.domain.enums.RecommendedPace;
import kr.io.pacer.core.domain.enums.SignalState;
import kr.io.pacer.core.dto.ai.AiRouteRequest;
import kr.io.pacer.core.dto.ai.AiRouteResponse;
import kr.io.pacer.core.dto.external.SpatResponse;
import kr.io.pacer.core.dto.external.SpatStateResponse;
import kr.io.pacer.core.dto.request.RouteRequest;
import kr.io.pacer.core.dto.response.RouteHistoryResponse;
import kr.io.pacer.core.dto.response.RouteResponse;
import kr.io.pacer.core.dto.response.RouteResponse.SignalCycle;
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
import java.util.Map;
import java.util.UUID;

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

    public RouteResponse findRoute(RouteRequest req, UUID userId) {
        log.info("[Route] 경로 탐색 시작 | userId={} origin=({},{}) dest=({},{})",
                userId,
                req.getOrigin().getLat(), req.getOrigin().getLng(),
                req.getDestination().getLat(), req.getDestination().getLng());

        List<CachedRoute> candidates = routeGeometryService.fetchAll(req, userId);

        List<Integer> itstIds = candidates.stream()
                .flatMap(r -> r.intersections().stream())
                .map(IntersectionInfo::itstId)
                .distinct()
                .toList();

        Map<Integer, SpatResponse> spatMap = itstIds.isEmpty() ? Map.of() : citsSpatClient.fetchAll(itstIds);
        Map<Integer, SpatStateResponse> stateMap = itstIds.isEmpty() ? Map.of() : citsSpatStateClient.fetchAll(itstIds);
        Map<Integer, Map<String, SignalCycle>> cycleMap = signalCycleRepository.findByItstIds(itstIds);

        itstIds.stream()
                .filter(id -> !spatMap.containsKey(id))
                .forEach(id -> log.warn("[CITS] 신호 데이터 누락 itstId={}", id));

        CachedRoute selected = selectRouteViaAi(userId, candidates, spatMap, stateMap, cycleMap);

        List<RouteResponse.SignalCheckpoint> checkpoints = buildCheckpoints(selected.intersections(), spatMap, selected.totalTimeSec());
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
            AiRouteRequest aiRequest = buildAiRequest(userId, candidates, spatMap, cycleMap);
            AiRouteResponse aiResponse = aiRouteClient.selectRoute(aiRequest);

            String optimalId = aiResponse.getOptimalRouteId(); // "route_001"
            int index = Integer.parseInt(optimalId.replace("route_", "")) - 1;
            return candidates.get(Math.max(0, Math.min(index, candidates.size() - 1)));
        } catch (Exception e) {
            log.warn("[AI] 경로 선택 실패, 첫 번째 경로 사용: {}", e.getMessage());
            return candidates.get(0);
        }
    }

    private AiRouteRequest buildAiRequest(UUID userId, List<CachedRoute> candidates,
                                           Map<Integer, SpatResponse> spatMap,
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

            List<AiRouteRequest.Crosswalk> crosswalks = route.intersections().stream()
                    .map(intersection -> {
                        SpatResponse spat = spatMap.get(intersection.itstId());
                        Map<String, SignalCycle> cycles = cycleMap.get(intersection.itstId());
                        return AiRouteRequest.Crosswalk.builder()
                                .crosswalkId(String.valueOf(intersection.itstId()))
                                .distanceFromStart(intersection.fraction() * route.totalDistanceM())
                                .signal(AiRouteRequest.Signal.builder()
                                        .phase(resolvePhase(spat))
                                        .remainingSeconds(resolveRemaining(spat))
                                        .cycleSeconds(resolveCycle(cycles))
                                        .build())
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

    private String resolvePhase(SpatResponse spat) {
        if (spat == null) return null;
        return hasAnyValidPdsg(
                spat.getNtPdsgRmdrCs(), spat.getEtPdsgRmdrCs(),
                spat.getStPdsgRmdrCs(), spat.getWtPdsgRmdrCs(),
                spat.getNePdsgRmdrCs(), spat.getSePdsgRmdrCs(),
                spat.getSwPdsgRmdrCs(), spat.getNwPdsgRmdrCs()) ? "green" : "red";
    }

    private Double resolveRemaining(SpatResponse spat) {
        if (spat == null) return null;
        Double[] values = {
                spat.getNtPdsgRmdrCs(), spat.getEtPdsgRmdrCs(),
                spat.getStPdsgRmdrCs(), spat.getWtPdsgRmdrCs(),
                spat.getNePdsgRmdrCs(), spat.getSePdsgRmdrCs(),
                spat.getSwPdsgRmdrCs(), spat.getNwPdsgRmdrCs()
        };
        for (Double v : values) {
            if (v != null && v > 0 && v < 36001.0) return v;
        }
        return null;
    }

    private Double resolveCycle(Map<String, SignalCycle> cycles) {
        if (cycles == null || cycles.isEmpty()) return null;
        SignalCycle cycle = cycles.values().iterator().next();
        if (cycle.getRedMaxSec() == null || cycle.getGreenMaxSec() == null) return null;
        return cycle.getRedMaxSec() + cycle.getGreenMaxSec();
    }

    private List<RouteResponse.SignalCheckpoint> buildCheckpoints(
            List<IntersectionInfo> intersections,
            Map<Integer, SpatResponse> spatMap,
            int totalTimeSec) {

        return intersections.stream()
                .filter(i -> spatMap.containsKey(i.itstId()))
                .map(i -> {
                    SpatResponse spat = spatMap.get(i.itstId());
                    int etaSec = (int) (i.fraction() * totalTimeSec);
                    SignalState state = resolveSignalState(spat);
                    return RouteResponse.SignalCheckpoint.builder()
                            .nodeId(i.itstId())
                            .lat(i.lat())
                            .lng(i.lng())
                            .etaFromStartSeconds(etaSec)
                            .signalState(state)
                            .recommendedPace(state == SignalState.GREEN ? RecommendedPace.NORMAL : RecommendedPace.SPEED_UP)
                            .build();
                })
                .toList();
    }

    private List<RouteResponse.IntersectionSignal> buildIntersectionSignals(
            List<IntersectionInfo> intersections,
            Map<Integer, SpatResponse> spatMap,
            Map<Integer, SpatStateResponse> stateMap,
            Map<Integer, Map<String, SignalCycle>> cycleMap) {

        return intersections.stream()
                .map(i -> {
                    RouteResponse.IntersectionSignal.IntersectionSignalBuilder builder =
                            RouteResponse.IntersectionSignal.builder()
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

    public List<RouteHistoryResponse> getHistory(UUID userId, Pageable pageable) {
        List<RouteHistoryResponse> result = historyRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .stream()
                .map(RouteHistoryResponse::from)
                .toList();
        log.debug("[Route] 히스토리 조회 | userId={} count={}", userId, result.size());
        return result;
    }

    private SignalState resolveSignalState(SpatResponse spat) {
        boolean hasValidGreen = hasAnyValidPdsg(
                spat.getNtPdsgRmdrCs(), spat.getEtPdsgRmdrCs(),
                spat.getStPdsgRmdrCs(), spat.getWtPdsgRmdrCs(),
                spat.getNePdsgRmdrCs(), spat.getSePdsgRmdrCs(),
                spat.getSwPdsgRmdrCs(), spat.getNwPdsgRmdrCs());
        return hasValidGreen ? SignalState.GREEN : SignalState.RED;
    }

    private boolean hasAnyValidPdsg(Double... values) {
        for (Double v : values) {
            if (v != null && v < 36001.0 && v > 0) return true;
        }
        return false;
    }
}
