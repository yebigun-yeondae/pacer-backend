package kr.io.pacer.core.client;

import kr.io.pacer.core.dto.external.SpbiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
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
        List<CompletableFuture<SpbiResponse>> futures = itstIds.stream()
                .map(id -> CompletableFuture.supplyAsync(() -> fetch(id)))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(SpbiResponse::getItstIdAsInt, item -> item));
    }

    private SpbiResponse fetch(int itstId) {
        try {
            List<SpbiResponse> list = restClient.get()
                    .uri(uri -> uri
                            .queryParam("apikey", apiKey)
                            .queryParam("type", "json")
                            .queryParam("numOfRows", 1)
                            .queryParam("pageNo", 1)
                            .queryParam("itstId", itstId)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (list == null || list.isEmpty()) return null;
            return list.get(0);
        } catch (Exception e) {
            log.warn("[CITS] 신호상태 조회 실패 itstId={} error={}", itstId, e.getMessage());
            return null;
        }
    }
}
