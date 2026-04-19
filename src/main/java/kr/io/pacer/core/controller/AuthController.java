package kr.io.pacer.core.controller;

import jakarta.validation.Valid;
import kr.io.pacer.core.dto.oauth2.AccessTokenDto;
import kr.io.pacer.core.dto.oauth2.GoogleProfileDto;
import kr.io.pacer.core.dto.oauth2.KakaoProfileDto;
import kr.io.pacer.core.dto.request.GoogleLoginRequest;
import kr.io.pacer.core.dto.request.KakaoLoginRequest;
import kr.io.pacer.core.dto.response.TokenResponse;
import kr.io.pacer.core.service.AuthService;
import kr.io.pacer.core.service.GoogleAuthService;
import kr.io.pacer.core.service.KakaoAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final KakaoAuthService kakaoAuthService;
    private final GoogleAuthService googleAuthService;
    private final AuthService authService;

    @PostMapping("/kakao") // 카카오 로그인
    public ResponseEntity<TokenResponse> kakaoLogin(
            @RequestBody @Valid KakaoLoginRequest request) {

        KakaoProfileDto profile = kakaoAuthService.getProfile(request.getAccessToken());
        return ResponseEntity.ok(authService.loginWithKakao(profile));
    }

    @PostMapping("/google") // 구글 로그인
    public ResponseEntity<TokenResponse> googleLogin(
            @RequestBody @Valid GoogleLoginRequest request) {

        AccessTokenDto accessToken = googleAuthService.getAccessToken(request.getAccessToken());
        GoogleProfileDto profile = googleAuthService.getProfile(accessToken.getAccessToken());
        return ResponseEntity.ok(authService.loginWithGoogle(profile));
    }

    @PostMapping("/reissue") // Access Token 재발급
    public ResponseEntity<TokenResponse> reissue(
            @RequestHeader("Refresh-Token") String refreshToken) {
        return ResponseEntity.ok(authService.reissue(refreshToken));
    }

    @PostMapping("/logout") // 로그아웃
    public ResponseEntity<Void> logout(
            @RequestHeader("Refresh-Token") String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent().build();
    }
}
