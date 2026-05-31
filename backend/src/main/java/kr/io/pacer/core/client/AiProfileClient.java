package kr.io.pacer.core.client;

import kr.io.pacer.core.dto.ai.AiProfileUpdateRequest;
import kr.io.pacer.core.dto.ai.AiProfileUpdateResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class AiProfileClient {

    private final RestClient restClient;

    public AiProfileClient(@Value("${ai.api-url}") String apiUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .requestFactory(factory)
                .build();
    }

    public AiProfileUpdateResponse updateProfile(AiProfileUpdateRequest request) {
        try {
            AiProfileUpdateResponse response = restClient.post()
                    .uri("/api/v1/user/profile/update")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiProfileUpdateResponse.class);

            if (response == null) throw new RuntimeException("AI 서버 응답 없음");
            log.info("[AI] 프로필 업데이트 응답 | updated={} skipReason={}", response.isUpdated(), response.getSkipReason());
            return response;

        } catch (HttpClientErrorException.UnprocessableEntity e) {
            log.warn("[AI] 프로필 업데이트 422 유효성 실패 | body={}", e.getResponseBodyAsString());
            throw e;
        }
    }
}
