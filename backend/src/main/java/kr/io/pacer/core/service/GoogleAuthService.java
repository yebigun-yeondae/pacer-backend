package kr.io.pacer.core.service;

import kr.io.pacer.core.dto.oauth2.GoogleProfileDto;
import kr.io.pacer.core.exception.InvalidTokenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class GoogleAuthService {

    private final RestClient restClient = RestClient.create();

    public GoogleProfileDto getProfile(String accessToken) {
        log.info("[Google] 프로필 조회 요청 | token prefix={}", accessToken.substring(0, Math.min(10, accessToken.length())));
        GoogleProfileDto profile = restClient.get()
                .uri("https://www.googleapis.com/oauth2/v3/userinfo")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .toEntity(GoogleProfileDto.class)
                .getBody();
        if (profile == null) {
            log.warn("[Google] 프로필 조회 실패 - 응답 없음");
            throw new InvalidTokenException("Google 액세스 토큰이 유효하지 않습니다.");
        }
        return profile;
    }
}
