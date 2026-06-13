package kr.io.pacer.core.client;

import kr.io.pacer.core.dto.external.SubwayArrivalApiResponse;
import kr.io.pacer.core.dto.external.SubwayArrivalApiResponse.RealtimeArrival;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class SubwayArrivalApiClient {

    private final RestClient restClient;
    private final String apiKey;

    public SubwayArrivalApiClient(
            @Value("${subway.arrival-api-url}") String apiUrl,
            @Value("${subway.arrival-api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .build();
    }

    public List<RealtimeArrival> fetchArrivals(String stationNm) {
        try {
            SubwayArrivalApiResponse response = restClient.get()
                    .uri("/api/subway/{key}/json/realtimeStationArrival/0/10/{stationNm}",
                            apiKey, stationNm)
                    .retrieve()
                    .body(SubwayArrivalApiResponse.class);

            if (response == null || response.getRealtimeArrivalList() == null) {
                return Collections.emptyList();
            }
            return response.getRealtimeArrivalList();
        } catch (Exception e) {
            log.warn("[Subway] 실시간 도착정보 조회 실패 | station={} error={}", stationNm, e.getMessage());
            return Collections.emptyList();
        }
    }
}
