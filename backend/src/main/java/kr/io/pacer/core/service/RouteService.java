package kr.io.pacer.core.service;

import kr.io.pacer.core.client.CitsSpatClient;
import kr.io.pacer.core.domain.RouteHistory;
import kr.io.pacer.core.domain.User;
import kr.io.pacer.core.domain.enums.RecommendedPace;
import kr.io.pacer.core.domain.enums.SignalState;
import kr.io.pacer.core.dto.external.SpatResponse;
import kr.io.pacer.core.dto.request.RouteRequest;
import kr.io.pacer.core.dto.response.RouteHistoryResponse;
import kr.io.pacer.core.dto.response.RouteResponse;
import kr.io.pacer.core.repository.jdbc.RouteRepository.IntersectionInfo;
import kr.io.pacer.core.repository.jpa.PedestrianProfileRepository;
import kr.io.pacer.core.repository.jpa.RouteHistoryRepository;
import kr.io.pacer.core.repository.jpa.UserRepository;
import kr.io.pacer.core.service.RouteGeometryService.CachedRoute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteGeometryService routeGeometryService;
    private final CitsSpatClient citsSpatClient;
    private final PedestrianProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final RouteHistoryRepository historyRepository;
    private final FavoritePlaceService favoritePlaceService;

    public RouteResponse findRoute(RouteRequest req, UUID userId) {
        log.info("[Route] 경로 탐색 시작 | userId={} origin=({},{}) dest=({},{})",
                userId,
                req.getOrigin().getLat(), req.getOrigin().getLng(),
                req.getDestination().getLat(), req.getDestination().getLng());

        CachedRoute cached = routeGeometryService.fetch(req, userId);

        List<Integer> itstIds = cached.intersections().stream().map(IntersectionInfo::itstId).toList();
        Map<Integer, SpatResponse> spatMap = itstIds.isEmpty() ? Map.of() : citsSpatClient.fetchAll(itstIds);

        cached.intersections().stream()
                .filter(i -> !spatMap.containsKey(i.itstId()))
                .forEach(i -> log.warn("[CITS] 신호 데이터 누락 itstId={} name={}", i.itstId(), i.name()));

        List<RouteResponse.SignalCheckpoint> checkpoints = buildCheckpoints(cached.intersections(), spatMap, cached.totalTimeSec());
        List<RouteResponse.IntersectionSignal> intersectionSignals = buildIntersectionSignals(cached.intersections(), spatMap);

        int signalStops = (int) checkpoints.stream()
                .filter(c -> c.getSignalState() == SignalState.RED).count();

        RouteResponse response = RouteResponse.builder()
                .polyline(cached.polyline())
                .totalTimeSeconds(cached.totalTimeSec())
                .totalDistanceMeters(cached.totalDistanceM())
                .signalCheckpoints(checkpoints)
                .intersectionSignals(intersectionSignals)
                .build();

        User user = userRepository.getReferenceById(userId);
        historyRepository.save(
                RouteHistory.of(user, req, cached.polyline(), cached.totalTimeSec(), cached.totalDistanceM(), signalStops));
        profileRepository.incrementRouteStats(userId, cached.totalDistanceM());
        favoritePlaceService.incrementVisitIfNearby(
                userId, req.getDestination().getLat(), req.getDestination().getLng());

        log.info("[Route] 경로 탐색 완료 | userId={} distance={}m time={}s stops={} intersections={}",
                userId, (int) cached.totalDistanceM(), cached.totalTimeSec(), signalStops, intersectionSignals.size());
        return response;
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
            Map<Integer, SpatResponse> spatMap) {

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
