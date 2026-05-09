package kr.io.pacer.core.service;

import kr.io.pacer.core.client.ValhallaClient;
import kr.io.pacer.core.dto.external.ValhallaResponse;
import kr.io.pacer.core.dto.request.RouteRequest;
import kr.io.pacer.core.repository.jdbc.RouteRepository;
import kr.io.pacer.core.repository.jdbc.RouteRepository.IntersectionInfo;
import kr.io.pacer.core.repository.jpa.PedestrianProfileRepository;
import kr.io.pacer.core.util.PolylineEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteGeometryService {

    private static final double DEFAULT_WALKING_SPEED_MPS = 1.4;

    private final ValhallaClient valhallaClient;
    private final RouteRepository routeRepository;
    private final PedestrianProfileRepository profileRepository;
    private final PolylineEncoder polylineEncoder;

    public record CachedRoute(
            String polyline,
            int totalTimeSec,
            double totalDistanceM,
            List<IntersectionInfo> intersections
    ) {}

    @Cacheable(
            value = "routes",
            key = "#userId + ':' + #req.origin.lat + ':' + #req.origin.lng + ':' + #req.destination.lat + ':' + #req.destination.lng"
    )
    public CachedRoute fetch(RouteRequest req, UUID userId) {
        double userSpeed = profileRepository.findByUserId(userId)
                .map(p -> p.getAvgSpeedMps())
                .orElse(DEFAULT_WALKING_SPEED_MPS);

        ValhallaResponse valhalla = valhallaClient.route(
                req.getOrigin().getLat(), req.getOrigin().getLng(),
                req.getDestination().getLat(), req.getDestination().getLng(),
                req.getMode(), userSpeed);

        ValhallaResponse.Leg leg = valhalla.getTrip().getLegs().get(0);
        String polyline = leg.getShape();
        int totalTimeSec = (int) valhalla.getTrip().getSummary().getTime();
        double totalDistanceM = valhalla.getTrip().getSummary().getLength() * 1000.0;

        String lineStringWkt = polylineEncoder.toWkt(polyline);
        List<IntersectionInfo> intersections = routeRepository.findIntersectionsByRouteWkt(lineStringWkt);
        log.info("[Route] 경로 위 교차로 수: {}", intersections.size());

        return new CachedRoute(polyline, totalTimeSec, totalDistanceM, intersections);
    }
}
