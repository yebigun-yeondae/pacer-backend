package kr.io.pacer.core.service;

import kr.io.pacer.core.client.CitsSpatClient;
import kr.io.pacer.core.domain.RouteHistory;
import kr.io.pacer.core.domain.TrafficSignal;
import kr.io.pacer.core.domain.User;
import kr.io.pacer.core.domain.enums.RecommendedPace;
import kr.io.pacer.core.domain.enums.SignalState;
import kr.io.pacer.core.dto.external.SpatResponse;
import kr.io.pacer.core.dto.internal.RouteSegment;
import kr.io.pacer.core.dto.request.RouteRequest;
import kr.io.pacer.core.dto.response.RouteResponse;
import kr.io.pacer.core.exception.RouteNotFoundException;
import kr.io.pacer.core.repository.*;
import kr.io.pacer.core.util.PolylineEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteRepository             routeRepository;
    private final TrafficSignalRepository     signalRepository;
    private final IntersectionRepository      intersectionRepository;
    private final PedestrianProfileRepository profileRepository;
    private final UserRepository              userRepository;
    private final RouteHistoryRepository      historyRepository;
    private final PolylineEncoder             polylineEncoder;
    private final CitsSpatClient              citsSpatClient;
    private final JdbcTemplate               jdbcTemplate;

    @Cacheable(
            value = "routes",
            key   = "#userId + ':' + #req.origin.lat + ':' + #req.origin.lng + ':' + #req.destination.lat + ':' + #req.destination.lng"
    )
    @Transactional
    public RouteResponse findRoute(RouteRequest req, UUID userId) {
        log.info("[Route] 경로 탐색 시작 | userId={} origin=({},{}) dest=({},{})",
                userId,
                req.getOrigin().getLat(), req.getOrigin().getLng(),
                req.getDestination().getLat(), req.getDestination().getLng());

        double userSpeed = profileRepository.findByUserId(userId)
                .map(p -> p.getAvgSpeedMps())
                .orElse(1.4);

        long startNode = routeRepository.findNearestNode(
                req.getOrigin().getLat(), req.getOrigin().getLng());
        long endNode = routeRepository.findNearestNode(
                req.getDestination().getLat(), req.getDestination().getLng());

        double nowEpochSec = Instant.now().getEpochSecond();

        List<RouteSegment> segments = routeRepository.findRoute(
                startNode, endNode, userSpeed, nowEpochSec,
                req.getOrigin().getLat(), req.getOrigin().getLng(),
                req.getDestination().getLat(), req.getDestination().getLng());

        if (segments.isEmpty()) {
            log.warn("[Route] 경로 없음 | userId={} startNode={} endNode={}", userId, startNode, endNode);
            throw new RouteNotFoundException("경로를 찾을 수 없습니다.");
        }

        String polyline = polylineEncoder.encode(segments);
        List<RouteResponse.SignalCheckpoint> checkpoints = buildCheckpoints(segments, userSpeed);
        List<RouteResponse.IntersectionSignal> intersectionSignals = buildIntersectionSignals(segments);

        double totalDistance = segments.stream()
                .mapToDouble(RouteSegment::getLengthMeters).sum();
        int totalTime = (int) segments.get(segments.size() - 1).getCumulativeSeconds();
        int signalStops = (int) checkpoints.stream()
                .filter(c -> c.getSignalState() == SignalState.RED).count();

        RouteResponse response = RouteResponse.builder()
                .polyline(polyline)
                .totalTimeSeconds(totalTime)
                .totalDistanceMeters(totalDistance)
                .signalCheckpoints(checkpoints)
                .intersectionSignals(intersectionSignals)
                .build();

        User user = userRepository.getReferenceById(userId);
        historyRepository.save(
                RouteHistory.of(user, req, polyline, totalTime, totalDistance, signalStops));
        profileRepository.findByUserId(userId)
                .ifPresent(p -> p.recordRoute(totalDistance));

        log.info("[Route] 경로 탐색 완료 | userId={} distance={}m time={}s signals={} intersections={}",
                userId, (int) totalDistance, totalTime, signalStops, intersectionSignals.size());
        return response;
    }

    private List<RouteResponse.IntersectionSignal> buildIntersectionSignals(List<RouteSegment> segments) {
        String lineStringWkt = buildLineStringWkt(segments);
        if (lineStringWkt == null) return List.of();

        log.info("[Route] linestring WKT 앞 100자: {}", lineStringWkt.substring(0, Math.min(100, lineStringWkt.length())));

        List<Integer> itstIds = intersectionRepository.findItstIdsByRouteWkt(lineStringWkt);
        log.info("[Route] 경로 위 교차로 수: {} ids={}", itstIds.size(), itstIds);
        if (itstIds.isEmpty()) return List.of();

        Map<Integer, SpatResponse> spatMap = citsSpatClient.fetchAll(itstIds);

        String sql = "SELECT itst_id, name, ST_Y(geom) AS lat, ST_X(geom) AS lng FROM intersections WHERE itst_id = ANY(?)";
        Map<Integer, RouteResponse.IntersectionSignal.IntersectionSignalBuilder> builderMap =
                jdbcTemplate.query(sql,
                        ps -> ps.setArray(1, ps.getConnection().createArrayOf("integer", itstIds.toArray())),
                        (rs, rowNum) -> {
                            int id = rs.getInt("itst_id");
                            return Map.entry(id, RouteResponse.IntersectionSignal.builder()
                                    .itstId(id)
                                    .name(rs.getString("name"))
                                    .lat(rs.getDouble("lat"))
                                    .lng(rs.getDouble("lng")));
                        })
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return itstIds.stream()
                .filter(builderMap::containsKey)
                .map(id -> {
                    RouteResponse.IntersectionSignal.IntersectionSignalBuilder builder = builderMap.get(id);
                    SpatResponse spat = spatMap.get(id);
                    if (spat != null) {
                        builder.ntPdsgRmdrCs(spat.getNtPdsgRmdrCs())
                               .etPdsgRmdrCs(spat.getEtPdsgRmdrCs())
                               .stPdsgRmdrCs(spat.getStPdsgRmdrCs())
                               .wtPdsgRmdrCs(spat.getWtPdsgRmdrCs())
                               .nePdsgRmdrCs(spat.getNePdsgRmdrCs())
                               .sePdsgRmdrCs(spat.getSePdsgRmdrCs())
                               .swPdsgRmdrCs(spat.getSwPdsgRmdrCs())
                               .nwPdsgRmdrCs(spat.getNwPdsgRmdrCs())
                               .signalState(resolveSignalState(spat));
                    } else {
                        builder.signalState(SignalState.UNKNOWN);
                    }
                    return builder.build();
                })
                .toList();
    }

    private SignalState resolveSignalState(SpatResponse spat) {
        List<Integer> values = Arrays.asList(
                spat.getNtPdsgRmdrCs(), spat.getEtPdsgRmdrCs(),
                spat.getStPdsgRmdrCs(), spat.getWtPdsgRmdrCs(),
                spat.getNePdsgRmdrCs(), spat.getSePdsgRmdrCs(),
                spat.getSwPdsgRmdrCs(), spat.getNwPdsgRmdrCs()
        );
        boolean allNull = values.stream().allMatch(v -> v == null);
        if (allNull) return SignalState.UNKNOWN;
        boolean anyPositive = values.stream().anyMatch(v -> v != null && v > 0);
        return anyPositive ? SignalState.GREEN : SignalState.RED;
    }

    private String buildLineStringWkt(List<RouteSegment> segments) {
        if (segments.size() < 2) return null;
        // 첫 세그먼트의 시작점을 포함하기 위해 geomWkt에서 첫 좌표 추출
        RouteSegment first = segments.get(0);
        String startPoint = first.getEndLng() + " " + first.getEndLat();
        String points = segments.stream()
                .map(s -> s.getEndLng() + " " + s.getEndLat())
                .collect(Collectors.joining(", "));
        return "LINESTRING(" + startPoint + ", " + points + ")";
    }

    private List<RouteResponse.SignalCheckpoint> buildCheckpoints(
            List<RouteSegment> segments, double userSpeed) {

        List<Long> nodeIds = segments.stream()
                .map(RouteSegment::getTargetNodeId).toList();
        Map<Long, TrafficSignal> signalMap = signalRepository.findByNodeIds(nodeIds);

        return segments.stream()
                .filter(s -> signalMap.containsKey(s.getTargetNodeId()))
                .map(s -> {
                    TrafficSignal signal = signalMap.get(s.getTargetNodeId());
                    double wait = signal.calcWaitSeconds(s.getCumulativeSeconds());

                    SignalState     state = wait == 0 ? SignalState.GREEN : SignalState.RED;
                    RecommendedPace pace  = resolvePace(signal, s.getCumulativeSeconds(), userSpeed);

                    return RouteResponse.SignalCheckpoint.builder()
                            .nodeId(s.getTargetNodeId())
                            .lat(s.getEndLat())
                            .lng(s.getEndLng())
                            .etaFromStartSeconds((int) s.getCumulativeSeconds())
                            .signalState(state)
                            .recommendedPace(pace)
                            .build();
                })
                .toList();
    }

    private RecommendedPace resolvePace(TrafficSignal signal,
                                        double etaSeconds, double userSpeed) {
        if (signal.calcWaitSeconds(etaSeconds * 0.85) == 0) return RecommendedPace.SPEED_UP;
        if (signal.calcWaitSeconds(etaSeconds * 1.15) == 0) return RecommendedPace.SLOW_DOWN;
        return RecommendedPace.NORMAL;
    }
}
