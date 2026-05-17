package kr.io.pacer.core.client;

import kr.io.pacer.core.dto.external.SpbiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CitsSpbiClient {

    private final RestClient restClient;
    private final String     apiKey;

    public CitsSpbiClient(
            @Value("${cits.spbi.url}") String apiUrl,
            @Value("${cits.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .build();
    }

    public Map<Integer, SpbiResponse> fetchAll(List<Integer> itstIds) {
        try {
            List<SpbiResponse> list = restClient.get()
                    .uri(uri -> uri
                            .queryParam("apikey", apiKey)
                            .queryParam("type", "json")
                            .queryParam("numOfRows", 200)
                            .queryParam("pageNo", 1)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (list == null || list.isEmpty()) return Map.of();

            Set<Integer> idSet = new HashSet<>(itstIds);
            return list.stream()
                    .filter(r -> {
                        try { return idSet.contains(r.getItstIdAsInt()); }
                        catch (NumberFormatException e) { return false; }
                    })
                    .collect(Collectors.toMap(SpbiResponse::getItstIdAsInt, r -> r, (a, b) -> b));
        } catch (Exception e) {
            log.warn("[CITS] 전체 신호상태 조회 실패 error={}", e.getMessage());
            return Map.of();
        }
    }
}
