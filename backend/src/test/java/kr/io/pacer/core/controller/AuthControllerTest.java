package kr.io.pacer.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.io.pacer.core.auth.JwtFilter;
import kr.io.pacer.core.config.SecurityConfig;
import kr.io.pacer.core.config.TestSecurityConfig;
import kr.io.pacer.core.dto.oauth2.GoogleProfileDto;
import kr.io.pacer.core.dto.oauth2.KakaoProfileDto;
import kr.io.pacer.core.dto.response.TokenResponse;
import kr.io.pacer.core.exception.DuplicateEmailException;
import kr.io.pacer.core.exception.InvalidCredentialsException;
import kr.io.pacer.core.exception.InvalidTokenException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import kr.io.pacer.core.service.AuthService;
import kr.io.pacer.core.service.GoogleAuthService;
import kr.io.pacer.core.service.KakaoAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    @MockitoBean KakaoAuthService kakaoAuthService;
    @MockitoBean GoogleAuthService googleAuthService;
    @MockitoBean AuthService authService;

    private static final UUID TEST_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000099");

    private Authentication mockAuth() {
        return new UsernamePasswordAuthenticationToken(
                TEST_USER_ID, null,
                List.of(new SimpleGrantedAuthority("USER"))
        );
    }

    // ── 일반 회원가입 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/auth/signup - 정상: 201 + 토큰 반환")
    void signup_validRequest_returns201WithTokens() throws Exception {
        given(authService.signup(any())).willReturn(new TokenResponse("access", "refresh"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@test.com\",\"password\":\"password1!\",\"nickname\":\"테스터\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/signup - 이메일 형식 오류: 400")
    void signup_invalidEmailFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"password1!\",\"nickname\":\"테스터\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/signup - 비밀번호 8자 미만: 400")
    void signup_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@test.com\",\"password\":\"pw\",\"nickname\":\"테스터\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/signup - 닉네임 1자(최소 2자 미만): 400")
    void signup_tooShortNickname_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@test.com\",\"password\":\"password1!\",\"nickname\":\"A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/signup - 중복 이메일: 409")
    void signup_duplicateEmail_returns409() throws Exception {
        given(authService.signup(any())).willThrow(new DuplicateEmailException("이미 사용 중인 이메일입니다."));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dup@test.com\",\"password\":\"password1!\",\"nickname\":\"테스터\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    // ── 일반 로그인 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/auth/login - 정상: 200 + 토큰 반환")
    void login_validCredentials_returns200WithTokens() throws Exception {
        given(authService.login(any())).willReturn(new TokenResponse("access", "refresh"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@test.com\",\"password\":\"password1!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - 이메일 빈 값: 400")
    void login_blankEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"password1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - 이메일·비밀번호 불일치: 401")
    void login_wrongCredentials_returns401() throws Exception {
        given(authService.login(any())).willThrow(new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다."));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@test.com\",\"password\":\"wrongpw\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

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

    // ── 회원탈퇴 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/v1/auth/withdraw - 정상: 204 No Content")
    void withdraw_authenticated_returns204() throws Exception {
        willDoNothing().given(authService).withdraw(TEST_USER_ID, "my-refresh");

        mockMvc.perform(delete("/api/v1/auth/withdraw")
                        .header("Refresh-Token", "my-refresh")
                        .with(authentication(mockAuth())))
                .andExpect(status().isNoContent());
    }

}
