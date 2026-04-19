package kr.io.pacer.core.service;

import kr.io.pacer.core.domain.RouteHistory;
import kr.io.pacer.core.domain.TrafficSignal;
import kr.io.pacer.core.domain.User;
import kr.io.pacer.core.domain.enums.RecommendedPace;
import kr.io.pacer.core.domain.enums.SignalState;
import kr.io.pacer.core.dto.internal.RouteSegment;
import kr.io.pacer.core.dto.request.RouteRequest;
import kr.io.pacer.core.dto.response.RouteResponse;
import kr.io.pacer.core.exception.RouteNotFoundException;
import kr.io.pacer.core.repository.*;
import kr.io.pacer.core.util.PolylineEncoder;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteRepository             routeRepository;
    private final TrafficSignalRepository     signalRepository;
    private final PedestrianProfileRepository profileRepository;
    private final UserRepository              userRepository;
    private final RouteHistoryRepository      historyRepository;
    private final PolylineEncoder             polylineEncoder;

    @Cacheable(
            value = "routes",
            key   = "#userId + ':' + #req.origin.lat + ':' + #req.origin.lng + ':' + #req.destination.lat + ':' + #req.destination.lng"
    )
    public RouteResponse findRouteWithCache(RouteRequest req, UUID userId) {
        return findRoute(req, userId);
    }

    @Transactional
    public RouteResponse findRoute(RouteRequest req, UUID userId) {
        double userSpeed = profileRepository.findByUserId(userId)
                .map(p -> p.getAvgSpeedMps())
                .orElse(1.4);

        long startNode = routeRepository.findNearestNode(
                req.getOrigin().getLat(), req.getOrigin().getLng());
        long endNode = routeRepository.findNearestNode(
                req.getDestination().getLat(), req.getDestination().getLng());

        double nowEpochSec = Instant.now().getEpochSecond();

        List<RouteSegment> segments =
                routeRepository.findRoute(startNode, endNode, userSpeed, nowEpochSec);

        if (segments.isEmpty()) {
            throw new RouteNotFoundException("경로를 찾을 수 없습니다.");
        }

        String polyline = polylineEncoder.encode(segments);
        List<RouteResponse.SignalCheckpoint> checkpoints = buildCheckpoints(segments, userSpeed);

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
                .build();

        User user = userRepository.getReferenceById(userId);
        historyRepository.save(
                RouteHistory.of(user, req, polyline, totalTime, totalDistance, signalStops));
        profileRepository.findByUserId(userId)
                .ifPresent(p -> p.recordRoute(totalDistance));

        return response;
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

    private String buildCacheKey(RouteRequest req, UUID userId) {
        return "route:%s:%.4f:%.4f:%.4f:%.4f".formatted(
                userId,
                req.getOrigin().getLat(), req.getOrigin().getLng(),
                req.getDestination().getLat(), req.getDestination().getLng()
        );
    }
}