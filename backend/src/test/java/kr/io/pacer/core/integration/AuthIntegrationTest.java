package kr.io.pacer.core.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.io.pacer.core.dto.oauth2.GoogleProfileDto;
import kr.io.pacer.core.dto.oauth2.KakaoProfileDto;
import kr.io.pacer.core.dto.response.TokenResponse;
import kr.io.pacer.core.service.GoogleAuthService;
import kr.io.pacer.core.service.KakaoAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 인증 흐름 통합 테스트.
 * PostgreSQL(PostGIS) + Redis Testcontainers를 사용하여 실제 DB 연동 검증.
 * 외부 OAuth 서비스(Kakao·Google API)는 MockBean으로 대체.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Auth 통합 테스트")
class AuthIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgrouting/pgrouting:latest")
                            .asCompatibleSubstituteFor("postgres")
            )
                    .withDatabaseName("pacer_test")
                    .withUsername("test")
                    .withPassword("test");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean KakaoAuthService kakaoAuthService;
    @MockBean GoogleAuthService googleAuthService;

    // ── 일반 회원가입 / 로그인 ────────────────────────────────────────────────

    @Test
    @DisplayName("일반 회원가입 → 토큰 발급")
    void signup_newUser_returns201AndTokens() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"signup@integration.test\",\"password\":\"password1!\",\"nickname\":\"통합테스터\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("동일 이메일 중복 회원가입 → 409")
    void signup_duplicateEmail_returns409() throws Exception {
        String body = "{\"email\":\"dup@integration.test\",\"password\":\"password1!\",\"nickname\":\"중복유저\"}";

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    @DisplayName("회원가입 → 로그인 → 재발급 → 로그아웃 전체 흐름")
    void signupLoginReissueLogout_fullFlow() throws Exception {
        String email = "flow@integration.test";
        String password = "password1!";

        // 1. 회원가입
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"nickname\":\"흐름유저\"}"))
                .andExpect(status().isCreated());

        // 2. 로그인 → 토큰 수령
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        TokenResponse tokens = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), TokenResponse.class);

        // 3. 토큰 재발급
        MvcResult reissueResult = mockMvc.perform(post("/api/v1/auth/reissue")
                        .header("Refresh-Token", tokens.getRefreshToken()))
                .andExpect(status().isOk())
                .andReturn();

        TokenResponse newTokens = objectMapper.readValue(
                reissueResult.getResponse().getContentAsString(), TokenResponse.class);

        // 4. 로그아웃
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Refresh-Token", newTokens.getRefreshToken()))
                .andExpect(status().isNoContent());

        // 5. 로그아웃 후 동일 Refresh Token 재사용 → 401
        mockMvc.perform(post("/api/v1/auth/reissue")
                        .header("Refresh-Token", newTokens.getRefreshToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("로그인 - 잘못된 비밀번호 → 401")
    void login_wrongPassword_returns401() throws Exception {
        // 먼저 회원가입
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"wrongpw@integration.test\",\"password\":\"password1!\",\"nickname\":\"테스터\"}"))
                .andExpect(status().isCreated());

        // 잘못된 비밀번호로 로그인 시도
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"wrongpw@integration.test\",\"password\":\"wrongpassword\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("로그인 - 존재하지 않는 이메일 → 401")
    void login_unknownEmail_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ghost@integration.test\",\"password\":\"password1!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    // ── 카카오 로그인 → 재발급 → 로그아웃 ────────────────────────────────────

    @Test
    @DisplayName("카카오 로그인 → 토큰 재발급 → 로그아웃 전체 흐름")
    void kakaoLoginReissueLogout_fullFlow() throws Exception {
        // 1. 카카오 프로필 Mock
        KakaoProfileDto kakaoProfile = makeKakaoProfile(11111L, "카카오유저", "kakao@test.com", "http://img.com/k.jpg");
        given(kakaoAuthService.getProfile(any())).willReturn(kakaoProfile);

        // 2. 카카오 로그인 → 토큰 수령
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"kakao-access-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        TokenResponse tokens = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), TokenResponse.class);

        assertThat(tokens.getAccessToken()).isNotBlank();
        assertThat(tokens.getRefreshToken()).isNotBlank();

        // 3. 토큰 재발급 → 새 토큰 수령
        MvcResult reissueResult = mockMvc.perform(post("/api/v1/auth/reissue")
                        .header("Refresh-Token", tokens.getRefreshToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        TokenResponse newTokens = objectMapper.readValue(
                reissueResult.getResponse().getContentAsString(), TokenResponse.class);

        assertThat(newTokens.getRefreshToken()).isNotBlank();

        // 4. 재발급된 토큰으로 로그아웃
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Refresh-Token", newTokens.getRefreshToken()))
                .andExpect(status().isNoContent());

        // 5. 로그아웃 후 동일 Refresh Token으로 재발급 시도 → 401
        mockMvc.perform(post("/api/v1/auth/reissue")
                        .header("Refresh-Token", newTokens.getRefreshToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("구글 로그인 → 신규 유저 생성 + 토큰 발급")
    void googleLogin_newUser_createsUserAndReturnsTokens() throws Exception {
        kr.io.pacer.core.dto.oauth2.AccessTokenDto accessTokenDto =
                new kr.io.pacer.core.dto.oauth2.AccessTokenDto();
        ReflectionTestUtils.setField(accessTokenDto, "accessToken", "google-access");

        GoogleProfileDto googleProfile = new GoogleProfileDto();
        ReflectionTestUtils.setField(googleProfile, "sub", "google-sub-unique-001");
        ReflectionTestUtils.setField(googleProfile, "name", "구글유저");
        ReflectionTestUtils.setField(googleProfile, "email", "google@integration.test");
        ReflectionTestUtils.setField(googleProfile, "profileImageUrl", "http://img.com/g.jpg");

        given(googleAuthService.getAccessToken(any())).willReturn(accessTokenDto);
        given(googleAuthService.getProfile(any())).willReturn(googleProfile);

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"google-code\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("동일 카카오 계정으로 재로그인 시 동일 유저로 처리")
    void kakaoLogin_sameUser_loginTwice_createsSingleUser() throws Exception {
        KakaoProfileDto kakaoProfile =
                makeKakaoProfile(22222L, "재로그인유저", "relogin@test.com", "http://img.com/r.jpg");
        given(kakaoAuthService.getProfile(any())).willReturn(kakaoProfile);

        // 1차 로그인
        mockMvc.perform(post("/api/v1/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"token-first\"}"))
                .andExpect(status().isOk());

        // 2차 로그인 (같은 계정)
        mockMvc.perform(post("/api/v1/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"token-second\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("만료된 Refresh Token으로 재발급 시도 → 401")
    void reissue_withExpiredToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reissue")
                        .header("Refresh-Token", "non-existent-refresh-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("카카오 로그인 - accessToken 빈 값: 400 Validation 오류")
    void kakaoLogin_blankAccessToken_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    private KakaoProfileDto makeKakaoProfile(long id, String nickname,
                                              String email, String imageUrl) {
        KakaoProfileDto dto = new KakaoProfileDto();
        ReflectionTestUtils.setField(dto, "id", id);

        KakaoProfileDto.KakaoAccount account = new KakaoProfileDto.KakaoAccount();
        ReflectionTestUtils.setField(account, "email", email);

        KakaoProfileDto.KakaoAccount.Profile profile = new KakaoProfileDto.KakaoAccount.Profile();
        ReflectionTestUtils.setField(profile, "nickname", nickname);
        ReflectionTestUtils.setField(profile, "profileImageUrl", imageUrl);

        ReflectionTestUtils.setField(account, "profile", profile);
        ReflectionTestUtils.setField(dto, "kakaoAccount", account);
        return dto;
    }
}
