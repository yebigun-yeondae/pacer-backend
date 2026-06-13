package kr.io.pacer.core.service;

import kr.io.pacer.core.client.SubwayArrivalApiClient;
import kr.io.pacer.core.dto.response.SubwayArrivalResponse;
import kr.io.pacer.core.dto.response.SubwayStationResponse;
import kr.io.pacer.core.repository.jpa.SubwayStationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubwayStationService {

    private static final double MAX_RADIUS_M = 5000.0;

    private final SubwayStationRepository subwayStationRepository;
    private final SubwayArrivalApiClient subwayArrivalApiClient;

    public List<SubwayStationResponse> findNearby(double lat, double lng, double radiusM) {
        double radius = Math.min(radiusM, MAX_RADIUS_M);
        log.info("[Subway] 근처 역 조회 | lat={} lng={} radius={}m", lat, lng, radius);
        return subwayStationRepository.findNearby(lat, lng, radius).stream()
                .map(SubwayStationResponse::from)
                .toList();
    }

    public List<SubwayArrivalResponse> findArrivals(String stationNm) {
        log.info("[Subway] 실시간 도착정보 조회 | stationNm={}", stationNm);
        return subwayArrivalApiClient.fetchArrivals(stationNm).stream()
                .map(SubwayArrivalResponse::from)
                .toList();
    }
}
