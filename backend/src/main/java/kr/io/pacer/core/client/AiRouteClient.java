package kr.io.pacer.core.client;

import kr.io.pacer.core.dto.ai.AiRouteRequest;
import kr.io.pacer.core.dto.ai.AiRouteResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class AiRouteClient {

    private final RestClient restClient;

    public AiRouteClient(@Value("${ai.api-url}") String apiUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .requestFactory(factory)
                .build();
    }

    public AiRouteResponse selectRoute(AiRouteRequest request) {
        AiRouteResponse response = restClient.post()
                .uri("/api/v1/route/optimize")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(AiRouteResponse.class);

        if (response == null) throw new RuntimeException("AI 서버 응답 없음");
        log.info("[AI] 경로 선택 완료 | optimalRouteId={} estimatedTime={}s",
                response.getOptimalRouteId(), response.getEstimatedTotalTimeSeconds());
        return response;
    }
}
