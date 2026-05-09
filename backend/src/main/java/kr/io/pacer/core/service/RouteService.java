package kr.io.pacer.core.service;

import kr.io.pacer.core.client.CitsSpatClient;
import kr.io.pacer.core.client.CitsSpbiClient;
import kr.io.pacer.core.domain.RouteHistory;
import kr.io.pacer.core.domain.User;
import kr.io.pacer.core.domain.enums.RecommendedPace;
import kr.io.pacer.core.domain.enums.SignalState;
import kr.io.pacer.core.dto.external.SpatResponse;
import kr.io.pacer.core.dto.external.SpbiResponse;
import kr.io.pacer.core.dto.request.RouteRequest;
import kr.io.pacer.core.dto.response.RouteResponse;
import kr.io.pacer.core.repository.jdbc.RouteRepository.IntersectionInfo;
import kr.io.pacer.core.repository.jpa.PedestrianProfileRepository;
import kr.io.pacer.core.repository.jpa.RouteHistoryRepository;
import kr.io.pacer.core.repository.jpa.UserRepository;
import kr.io.pacer.core.service.RouteGeometryService.CachedRoute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final CitsSpbiClient citsSpbiClient;
    private final PedestrianProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final RouteHistoryRepository historyRepository;
    private final FavoritePlaceService favoritePlaceService;

    @Transactional
    public RouteResponse findRoute(RouteRequest req, UUID userId) {
        log.info("[Route] 경로 탐색 시작 | userId={} origin=({},{}) dest=({},{})",
                userId,
                req.getOrigin().getLat(), req.getOrigin().getLng(),
                req.getDestination().getLat(), req.getDestination().getLng());

        CachedRoute cached = routeGeometryService.fetch(req, userId);

        List<Integer> itstIds = cached.intersections().stream().map(IntersectionInfo::itstId).toList();
        Map<Integer, SpatResponse> spatMap  = itstIds.isEmpty() ? Map.of() : citsSpatClient.fetchAll(itstIds);
        Map<Integer, SpbiResponse> spbiMap  = itstIds.isEmpty() ? Map.of() : citsSpbiClient.fetchAll(itstIds);

        List<RouteResponse.SignalCheckpoint> checkpoints = buildCheckpoints(cached.intersections(), spatMap, spbiMap, cached.totalTimeSec());
        List<RouteResponse.IntersectionSignal> intersectionSignals = buildIntersectionSignals(cached.intersections(), spatMap, spbiMap);

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
        profileRepository.findByUserId(userId)
                .ifPresent(p -> p.recordRoute(cached.totalDistanceM()));
        favoritePlaceService.incrementVisitIfNearby(
                userId, req.getDestination().getLat(), req.getDestination().getLng());

        log.info("[Route] 경로 탐색 완료 | userId={} distance={}m time={}s stops={} intersections={}",
                userId, (int) cached.totalDistanceM(), cached.totalTimeSec(), signalStops, intersectionSignals.size());
        return response;
    }

    private List<RouteResponse.SignalCheckpoint> buildCheckpoints(
            List<IntersectionInfo> intersections,
            Map<Integer, SpatResponse> spatMap,
            Map<Integer, SpbiResponse> spbiMap,
            int totalTimeSec) {

        return intersections.stream()
                .filter(i -> spbiMap.containsKey(i.itstId()))
                .map(i -> {
                    SpbiResponse spbi = spbiMap.get(i.itstId());
                    int etaSec = (int) (i.fraction() * totalTimeSec);
                    SignalState state = resolveSignalStateFromSpbi(spbi);
                    return RouteResponse.SignalCheckpoint.builder()
                            .nodeId(i.itstId())
                            .lat(i.lat())
                            .lng(i.lng())
                            .etaFromStartSeconds(etaSec)
                            .signalState(state)
                            .recommendedPace(RecommendedPace.NORMAL)
                            .build();
                })
                .toList();
    }

    private List<RouteResponse.IntersectionSignal> buildIntersectionSignals(
            List<IntersectionInfo> intersections,
            Map<Integer, SpatResponse> spatMap,
            Map<Integer, SpbiResponse> spbiMap) {

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

                    SpbiResponse spbi = spbiMap.get(i.itstId());
                    if (spbi != null) {
                        builder.ntPdsgStatNm(spbi.getNtPdsgStatNm())
                               .etPdsgStatNm(spbi.getEtPdsgStatNm())
                               .stPdsgStatNm(spbi.getStPdsgStatNm())
                               .wtPdsgStatNm(spbi.getWtPdsgStatNm())
                               .nePdsgStatNm(spbi.getNePdsgStatNm())
                               .sePdsgStatNm(spbi.getSePdsgStatNm())
                               .swPdsgStatNm(spbi.getSwPdsgStatNm())
                               .nwPdsgStatNm(spbi.getNwPdsgStatNm());
                    }
                    return builder.build();
                })
                .toList();
    }

    private SignalState resolveSignalStateFromSpbi(SpbiResponse spbi) {
        String[] statNames = {
                spbi.getNtPdsgStatNm(), spbi.getEtPdsgStatNm(),
                spbi.getStPdsgStatNm(), spbi.getWtPdsgStatNm(),
                spbi.getNePdsgStatNm(), spbi.getSePdsgStatNm(),
                spbi.getSwPdsgStatNm(), spbi.getNwPdsgStatNm()
        };
        boolean allNull = true;
        for (String stat : statNames) {
            if (stat == null) continue;
            allNull = false;
            if (stat.equals("permissive-Movement-Allowed") || stat.equals("protected-Movement-Allowed")) {
                return SignalState.GREEN;
            }
        }
        return allNull ? SignalState.UNKNOWN : SignalState.RED;
    }
}
