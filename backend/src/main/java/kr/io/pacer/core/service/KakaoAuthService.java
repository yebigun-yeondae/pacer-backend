package kr.io.pacer.core.service;

import kr.io.pacer.core.dto.oauth2.KakaoProfileDto;
import kr.io.pacer.core.exception.InvalidTokenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class KakaoAuthService {

    private final RestClient restClient = RestClient.create();

    public KakaoProfileDto getProfile(String accessToken) {
        log.info("[Kakao] 프로필 조회 요청 | token prefix={}", accessToken.substring(0, Math.min(10, accessToken.length())));
        KakaoProfileDto profile = restClient.get()
                .uri("https://kapi.kakao.com/v2/user/me")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .toEntity(KakaoProfileDto.class)
                .getBody();
        if (profile == null) {
            log.warn("[Kakao] 프로필 조회 실패 - 응답 없음");
            throw new InvalidTokenException("Kakao 액세스 토큰이 유효하지 않습니다.");
        }
        return profile;
    }
}
