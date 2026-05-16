package kr.io.pacer.core.service;

import kr.io.pacer.core.dto.response.BusStopResponse;
import kr.io.pacer.core.exception.BusStopNotFoundException;
import kr.io.pacer.core.repository.jpa.BusStopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusStopService {

    private static final double MAX_RADIUS_M = 5000.0;

    private final BusStopRepository busStopRepository;

    public List<BusStopResponse> findNearby(double lat, double lng, double radiusM) {
        double radius = Math.min(radiusM, MAX_RADIUS_M);
        log.info("[BusStop] 근처 정류장 조회 | lat={} lng={} radius={}m", lat, lng, radius);
        return busStopRepository.findNearby(lat, lng, radius).stream()
                .map(BusStopResponse::from)
                .toList();
    }

    public List<BusStopResponse> search(String keyword) {
        log.info("[BusStop] 정류장 검색 | keyword={}", keyword);
        return busStopRepository.findByNameContainingIgnoreCase(keyword).stream()
                .map(BusStopResponse::from)
                .toList();
    }

    public BusStopResponse findById(String stopId) {
        log.info("[BusStop] 정류장 단건 조회 | stopId={}", stopId);
        return busStopRepository.findById(stopId)
                .map(BusStopResponse::from)
                .orElseThrow(() -> new BusStopNotFoundException("존재하지 않는 정류장입니다."));
    }
}
