package kr.io.pacer.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.io.pacer.core.auth.JwtFilter;
import kr.io.pacer.core.config.SecurityConfig;
import kr.io.pacer.core.config.TestSecurityConfig;
import kr.io.pacer.core.dto.response.ProfileResponse;
import kr.io.pacer.core.service.ProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = ProfileController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtFilter.class}
        )
)
@Import(TestSecurityConfig.class)
@DisplayName("ProfileController 컨트롤러 테스트")
class ProfileControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ProfileService profileService;

    private static final UUID TEST_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private Authentication mockAuth() {
        return new UsernamePasswordAuthenticationToken(
                TEST_USER_ID, null,
                List.of(new SimpleGrantedAuthority("USER"))
        );
    }

    // ── 프로필 조회 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/profile - 정상: 200 + 프로필 반환")
    void getProfile_authenticated_returns200WithProfile() throws Exception {
        ProfileResponse profileResponse = ProfileResponse.builder()
                .nickname("테스터")
                .email("test@test.com")
                .profileImageUrl("http://img.com/1.jpg")
                .avgSpeedMps(1.4)
                .totalRoutes(5)
                .totalDistanceM(3500.0)
                .build();

        given(profileService.getProfile(TEST_USER_ID)).willReturn(profileResponse);

        mockMvc.perform(get("/api/v1/profile")
                        .with(authentication(mockAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("테스터"))
                .andExpect(jsonPath("$.email").value("test@test.com"))
                .andExpect(jsonPath("$.avgSpeedMps").value(1.4))
                .andExpect(jsonPath("$.totalRoutes").value(5))
                .andExpect(jsonPath("$.totalDistanceM").value(3500.0));
    }

    @Test
    @DisplayName("GET /api/v1/profile - 인증 없음: 보안 상관없이 서비스 호출 안 함")
    void getProfile_noAuth_serviceNotCalled() throws Exception {
        mockMvc.perform(get("/api/v1/profile"));
        then(profileService).should(never()).getProfile(any());
    }

    // ── 걸음속도 업데이트 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /api/v1/profile/walking-speed - 정상: 204 No Content")
    void updateWalkingSpeed_validRequest_returns204() throws Exception {
        willDoNothing().given(profileService).updateWalkingSpeed(eq(TEST_USER_ID), any());

        String body = """
                {
                    "segments": [
                        {"distanceM": 100.0, "durationS": 80.0, "slopeDeg": 0.0}
                    ]
                }
                """;

        mockMvc.perform(patch("/api/v1/profile/walking-speed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(authentication(mockAuth())))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("PATCH /api/v1/profile/walking-speed - segments 빈 배열: 400")
    void updateWalkingSpeed_emptySegments_returns400() throws Exception {
        String body = """
                {"segments": []}
                """;

        mockMvc.perform(patch("/api/v1/profile/walking-speed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(authentication(mockAuth())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("PATCH /api/v1/profile/walking-speed - segments null: 400")
    void updateWalkingSpeed_nullSegments_returns400() throws Exception {
        String body = "{}";

        mockMvc.perform(patch("/api/v1/profile/walking-speed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(authentication(mockAuth())))
                .andExpect(status().isBadRequest());
    }
}
