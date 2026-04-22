package kr.io.pacer.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.io.pacer.core.auth.JwtFilter;
import kr.io.pacer.core.config.SecurityConfig;
import kr.io.pacer.core.config.TestSecurityConfig;
import kr.io.pacer.core.dto.oauth2.AccessTokenDto;
import kr.io.pacer.core.dto.oauth2.GoogleProfileDto;
import kr.io.pacer.core.dto.oauth2.KakaoProfileDto;
import kr.io.pacer.core.dto.response.TokenResponse;
import kr.io.pacer.core.exception.InvalidTokenException;
import kr.io.pacer.core.service.AuthService;
import kr.io.pacer.core.service.GoogleAuthService;
import kr.io.pacer.core.service.KakaoAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtFilter.class}
        )
)
@Import(TestSecurityConfig.class)
@DisplayName("AuthController 컨트롤러 테스트")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean KakaoAuthService kakaoAuthService;
    @MockBean GoogleAuthService googleAuthService;
    @MockBean AuthService authService;

    // ── 카카오 로그인 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/auth/kakao - 정상: 200 + 토큰 반환")
    void kakaoLogin_validRequest_returns200WithTokens() throws Exception {
        given(kakaoAuthService.getProfile(any())).willReturn(new KakaoProfileDto());
        given(authService.loginWithKakao(any())).willReturn(new TokenResponse("access", "refresh"));

        mockMvc.perform(post("/api/v1/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"kakao-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/kakao - accessToken 빈 값: 400")
    void kakaoLogin_blankToken_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/kakao - 요청 본문 없음: 400")
    void kakaoLogin_missingBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── 구글 로그인 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/auth/google - 정상: 200 + 토큰 반환")
    void googleLogin_validRequest_returns200WithTokens() throws Exception {
        AccessTokenDto accessTokenDto = new AccessTokenDto();
        ReflectionTestUtils.setField(accessTokenDto, "accessToken", "google-access");

        given(googleAuthService.getAccessToken(any())).willReturn(accessTokenDto);
        given(googleAuthService.getProfile(any())).willReturn(new GoogleProfileDto());
        given(authService.loginWithGoogle(any())).willReturn(new TokenResponse("access", "refresh"));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"google-code\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/google - accessToken 빈 값: 400")
    void googleLogin_blankToken_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    // ── Access Token 재발급 ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/auth/reissue - 정상: 200 + 새 토큰 반환")
    void reissue_validRefreshToken_returns200() throws Exception {
        given(authService.reissue("valid-refresh"))
                .willReturn(new TokenResponse("new-access", "new-refresh"));

        mockMvc.perform(post("/api/v1/auth/reissue")
                        .header("Refresh-Token", "valid-refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/reissue - 유효하지 않은 토큰: 401")
    void reissue_invalidToken_returns401() throws Exception {
        given(authService.reissue("bad-token"))
                .willThrow(new InvalidTokenException("유효하지 않은 Refresh Token"));

        mockMvc.perform(post("/api/v1/auth/reissue")
                        .header("Refresh-Token", "bad-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    // ── 로그아웃 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/auth/logout - 정상: 204 No Content")
    void logout_validRefreshToken_returns204() throws Exception {
        willDoNothing().given(authService).logout("my-refresh");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Refresh-Token", "my-refresh"))
                .andExpect(status().isNoContent());
    }
}
