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
            key = "#userId + ':' + #req.mode + ':' + #req.origin.lat + ':' + #req.origin.lng + ':' + #req.destination.lat + ':' + #req.destination.lng + ':' + #req.waypoints.![lat + ',' + lng]"
    )
    public CachedRoute fetch(RouteRequest req, UUID userId) {
        double userSpeed = profileRepository.findByUserId(userId)
                .map(p -> p.getAvgSpeedMps())
                .orElse(DEFAULT_WALKING_SPEED_MPS);

        List<RouteRequest.Coordinate> wps = req.getWaypoints() != null ? req.getWaypoints() : List.of();
        List<double[]> waypointCoords = wps.stream()
                .map(wp -> new double[]{wp.getLat(), wp.getLng()})
                .toList();

        long t1 = System.currentTimeMillis();
        ValhallaResponse valhalla = valhallaClient.route(
                req.getOrigin().getLat(), req.getOrigin().getLng(),
                req.getDestination().getLat(), req.getDestination().getLng(),
                waypointCoords, req.getMode(), userSpeed);
        log.info("[Route] Valhalla 응답: {}ms", System.currentTimeMillis() - t1);

        List<ValhallaResponse.Leg> legs = valhalla.getTrip().getLegs();
        String polyline = legs.size() == 1
                ? legs.get(0).getShape()
                : polylineEncoder.merge(legs.stream().map(ValhallaResponse.Leg::getShape).toList());
        int totalTimeSec = (int) valhalla.getTrip().getSummary().getTime();
        double totalDistanceM = valhalla.getTrip().getSummary().getLength() * 1000.0;

        long t2 = System.currentTimeMillis();
        String lineStringWkt = polylineEncoder.toWkt(polyline);
        List<IntersectionInfo> intersections = routeRepository.findIntersectionsByRouteWkt(lineStringWkt);
        log.info("[Route] 교차로 공간 쿼리: {}ms | 교차로 수: {}", System.currentTimeMillis() - t2, intersections.size());

        return new CachedRoute(polyline, totalTimeSec, totalDistanceM, intersections);
    }
}
