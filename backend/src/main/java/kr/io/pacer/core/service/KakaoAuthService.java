package kr.io.pacer.core.service;

import kr.io.pacer.core.dto.oauth2.KakaoProfileDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class KakaoAuthService {

    private final RestClient restClient = RestClient.create();

    public KakaoProfileDto getProfile(String accessToken) {
        log.info("[Kakao] 프로필 조회 요청 | token prefix={}", accessToken.substring(0, Math.min(10, accessToken.length())));
        return restClient.get()
                .uri("https://kapi.kakao.com/v2/user/me")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .toEntity(KakaoProfileDto.class)
                .getBody();
    }
}
