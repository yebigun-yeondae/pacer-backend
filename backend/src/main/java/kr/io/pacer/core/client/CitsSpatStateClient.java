package kr.io.pacer.core.client;

import kr.io.pacer.core.dto.external.SpatStateResponse;
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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CitsSpatStateClient {

    private static final Executor STATE_IO_EXECUTOR = Executors.newFixedThreadPool(20);

    private final RestClient restClient;
    private final String     apiKey;

    public CitsSpatStateClient(
            @Value("${cits.state-api-url}") String apiUrl,
            @Value("${cits.api-key}") String apiKey) {
        this.apiKey = apiKey;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .requestFactory(factory)
                .build();
    }

    public Map<Integer, SpatStateResponse> fetchAll(List<Integer> itstIds) {
        List<CompletableFuture<SpatStateResponse>> futures = itstIds.stream()
                .map(id -> CompletableFuture.supplyAsync(() -> fetch(id), STATE_IO_EXECUTOR))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(SpatStateResponse::getItstIdAsInt, item -> item, (a, b) -> a));
    }

    private SpatStateResponse fetch(int itstId) {
        try {
            List<SpatStateResponse> list = restClient.get()
                    .uri(uri -> uri
                            .queryParam("apikey", apiKey)
                            .queryParam("itstId", itstId)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (list == null || list.isEmpty()) return null;
            return list.get(0);
        } catch (Exception e) {
            log.warn("[CITS-State] 신호상태 조회 실패 itstId={} error={}", itstId, e.getMessage());
            return null;
        }
    }
}
