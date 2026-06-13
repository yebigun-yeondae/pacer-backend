package kr.io.pacer.core.client;

import kr.io.pacer.core.dto.external.SubwayStationApiResponse;
import kr.io.pacer.core.dto.external.SubwayStationApiResponse.Row;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class SubwayStationApiClient {

    private static final int PAGE_SIZE = 500;

    private final RestClient restClient;
    private final String apiKey;

    public SubwayStationApiClient(
            @Value("${subway.station-api-url}") String apiUrl,
            @Value("${subway.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .build();
    }

    public List<Row> fetchAll() {
        List<Row> result = new ArrayList<>();
        int startIndex = 1;

        while (true) {
            int endIndex = startIndex + PAGE_SIZE - 1;
            SubwayStationApiResponse.Service service = fetchPage(startIndex, endIndex);

            if (service == null || service.getRow() == null || service.getRow().isEmpty()) break;

            result.addAll(service.getRow());
            log.debug("[Subway] 역사 조회 {}-{} fetched={} total={}",
                    startIndex, endIndex, service.getRow().size(), service.getListTotalCount());

            if (result.size() >= service.getListTotalCount()) break;
            startIndex += PAGE_SIZE;
        }

        log.info("[Subway] 역사마스터 전체 조회 완료 | {}개역", result.size());
        return result;
    }

    private SubwayStationApiResponse.Service fetchPage(int startIndex, int endIndex) {
        try {
            SubwayStationApiResponse response = restClient.get()
                    .uri("/{key}/json/subwayStationMaster/{start}/{end}/",
                            apiKey, startIndex, endIndex)
                    .retrieve()
                    .body(SubwayStationApiResponse.class);

            return (response != null) ? response.getSubwayStationMaster() : null;
        } catch (Exception e) {
            log.warn("[Subway] 역사마스터 페이지 조회 실패 | {}-{} error={}", startIndex, endIndex, e.getMessage());
            return null;
        }
    }
}
