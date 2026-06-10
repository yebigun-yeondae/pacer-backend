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
        log.info("[AI] 경로 최적화 요청 | userId={} candidates={}",
                request.getUserId(), request.getRouteCandidates().size());
        log.debug("[AI] 요청 상세 | userProfile=(avgSpeed={}, speedStd={}, tripCount={}) candidates={}",
                request.getUserProfile().getAvgSpeed(),
                request.getUserProfile().getSpeedStd(),
                request.getUserProfile().getTripCount(),
                request.getRouteCandidates().stream()
                        .map(c -> String.format("%s(dist=%.0fm, crosswalks=%d)",
                                c.getRouteId(), c.getTotalDistance(), c.getCrosswalks().size()))
                        .toList());

        AiRouteResponse response = restClient.post()
                .uri("/api/v1/route/optimize")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(AiRouteResponse.class);

        if (response == null) throw new RuntimeException("AI 서버 응답 없음");
        log.info("[AI] 경로 선택 완료 | optimalRouteId={} estimatedTime={}s",
                response.getOptimalRouteId(), response.getEstimatedTotalTimeSeconds());
        log.debug("[AI] 응답 상세 | waitTime={}s warnings={} simulationDetails={}",
                response.getEstimatedWaitTimeSeconds(),
                response.getWarnings() == null ? 0 : response.getWarnings().size(),
                response.getSimulationDetails() == null ? "[]" :
                        response.getSimulationDetails().stream()
                                .map(d -> String.format("%s(total=%.0fs, wait=%.0fs, cits=%.0f%%)",
                                        d.getRouteId(), d.getTotalTimeSeconds(),
                                        d.getWaitTimeSeconds(), d.getCitsCoverageRate() * 100))
                                .toList());
        return response;
    }
}
