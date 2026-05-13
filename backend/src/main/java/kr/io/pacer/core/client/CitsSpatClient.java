package kr.io.pacer.core.client;

import kr.io.pacer.core.dto.external.SpatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CitsSpatClient {

    private final RestClient restClient;
    private final String     apiKey;

    public CitsSpatClient(
            @Value("${cits.api-url}") String apiUrl,
            @Value("${cits.api-key}") String apiKey) {
        this.apiKey = apiKey;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .requestFactory(factory)
                .build();
    }

    public Map<Integer, SpatResponse> fetchAll(List<Integer> itstIds) {
        List<CompletableFuture<SpatResponse>> futures = itstIds.stream()
                .map(id -> CompletableFuture.supplyAsync(() -> fetch(id)))
                .toList();

        return futures.stream()
                .map(f -> {
                    try {
                        return f.get(11, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        log.warn("[CITS] fetchAll timeout itstId 응답 무시: {}", e.getMessage());
                        f.cancel(true);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(SpatResponse::getItstIdAsInt, item -> item));
    }

    private SpatResponse fetch(int itstId) {
        try {
            List<SpatResponse> list = restClient.get()
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
            log.warn("[CITS] 신호 조회 실패 itstId={} error={}", itstId, e.getMessage());
            return null;
        }
    }
}
