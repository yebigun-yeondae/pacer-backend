package kr.io.pacer.core.client;

import kr.io.pacer.core.domain.enums.RouteMode;
import kr.io.pacer.core.dto.external.ValhallaResponse;
import kr.io.pacer.core.exception.RouteNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ValhallaClient {

    private final RestClient restClient;

    public ValhallaClient(@Value("${valhalla.url}") String valhallaUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        this.restClient = RestClient.builder()
                .baseUrl(valhallaUrl)
                .requestFactory(factory)
                .build();
    }

    public ValhallaResponse route(double originLat, double originLng,
                                  double destLat, double destLng,
                                  List<double[]> waypoints,
                                  RouteMode mode, double walkingSpeedMps) {
        double walkingSpeedKmh = Math.round(walkingSpeedMps * 3.6 * 10.0) / 10.0;

        Map<String, Object> pedestrianOptions = new HashMap<>();
        pedestrianOptions.put("walking_speed", walkingSpeedKmh);
        if (mode == RouteMode.SHORTEST) {
            pedestrianOptions.put("shortest", true);
        }

        List<Map<String, Object>> locations = new ArrayList<>();
        locations.add(Map.of("lat", originLat, "lon", originLng, "type", "break"));
        for (double[] wp : waypoints) {
            locations.add(Map.of("lat", wp[0], "lon", wp[1], "type", "break"));
        }
        locations.add(Map.of("lat", destLat, "lon", destLng, "type", "break"));

        Map<String, Object> body = new HashMap<>();
        body.put("locations", locations);
        body.put("costing", "pedestrian");
        body.put("costing_options", Map.of("pedestrian", pedestrianOptions));
        body.put("shape_format", "polyline6");
        body.put("directions_options", Map.of("language", "ko-KR"));

        try {
            ValhallaResponse response = restClient.post()
                    .uri("/route")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(ValhallaResponse.class);

            if (response == null || response.getTrip() == null
                    || response.getTrip().getLegs() == null
                    || response.getTrip().getLegs().isEmpty()) {
                throw new RouteNotFoundException("경로를 찾을 수 없습니다.");
            }
            return response;
        } catch (RouteNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Valhalla] 경로 탐색 실패: {}", e.getMessage());
            throw new RouteNotFoundException("경로를 찾을 수 없습니다.");
        }
    }
}
