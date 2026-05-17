package kr.io.pacer.core.service;

import kr.io.pacer.core.dto.oauth2.GoogleProfileDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    public GoogleProfileDto getProfile(String accessToken) {
        log.info("[Google] 프로필 조회 요청 | token prefix={}", accessToken.substring(0, Math.min(10, accessToken.length())));
        return RestClient.create()
                .get()
                .uri("https://www.googleapis.com/oauth2/v3/userinfo")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .toEntity(GoogleProfileDto.class)
                .getBody();
    }
}
