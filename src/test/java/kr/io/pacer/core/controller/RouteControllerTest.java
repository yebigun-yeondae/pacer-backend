package kr.io.pacer.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.io.pacer.core.auth.JwtFilter;
import kr.io.pacer.core.config.SecurityConfig;
import kr.io.pacer.core.config.TestSecurityConfig;
import kr.io.pacer.core.dto.response.RouteResponse;
import kr.io.pacer.core.exception.RouteNotFoundException;
import kr.io.pacer.core.service.RouteService;
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
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = RouteController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtFilter.class}
        )
)
@Import(TestSecurityConfig.class)
@DisplayName("RouteController 컨트롤러 테스트")
class RouteControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean RouteService routeService;

    private static final UUID TEST_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");

    private Authentication mockAuth() {
        return new UsernamePasswordAuthenticationToken(
                TEST_USER_ID, null,
                List.of(new SimpleGrantedAuthority("USER"))
        );
    }

    private static final String VALID_ROUTE_REQUEST = """
            {
                "origin": {"lat": 37.5, "lng": 127.0},
                "destination": {"lat": 37.51, "lng": 127.01},
                "originName": "출발지",
                "destinationName": "도착지"
            }
            """;

    @Test
    @DisplayName("POST /api/v1/routes - 정상: 200 + 경로 정보 반환")
    void getRoute_validRequest_returns200WithRouteResponse() throws Exception {
        RouteResponse response = RouteResponse.builder()
                .polyline("encodedPolyline")
                .totalTimeSeconds(300)
                .totalDistanceMeters(420.0)
                .signalCheckpoints(List.of())
                .build();

        given(routeService.findRoute(any(), any())).willReturn(response);

        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ROUTE_REQUEST)
                        .with(authentication(mockAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.polyline").value("encodedPolyline"))
                .andExpect(jsonPath("$.totalTimeSeconds").value(300))
                .andExpect(jsonPath("$.totalDistanceMeters").value(420.0))
                .andExpect(jsonPath("$.signalCheckpoints").isArray());
    }

    @Test
    @DisplayName("POST /api/v1/routes - origin null: 400")
    void getRoute_nullOrigin_returns400() throws Exception {
        String body = """
                {
                    "destination": {"lat": 37.51, "lng": 127.01}
                }
                """;

        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(authentication(mockAuth())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("POST /api/v1/routes - destination null: 400")
    void getRoute_nullDestination_returns400() throws Exception {
        String body = """
                {
                    "origin": {"lat": 37.5, "lng": 127.0}
                }
                """;

        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(authentication(mockAuth())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("POST /api/v1/routes - 경로 없음: 404")
    void getRoute_routeNotFound_returns404() throws Exception {
        given(routeService.findRoute(any(), any()))
                .willThrow(new RouteNotFoundException("경로를 찾을 수 없습니다."));

        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ROUTE_REQUEST)
                        .with(authentication(mockAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/routes - 위도 범위 초과: 400")
    void getRoute_latOutOfRange_returns400() throws Exception {
        String body = """
                {
                    "origin": {"lat": 100.0, "lng": 127.0},
                    "destination": {"lat": 37.51, "lng": 127.01}
                }
                """;

        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(authentication(mockAuth())))
                .andExpect(status().isBadRequest());
    }
}
