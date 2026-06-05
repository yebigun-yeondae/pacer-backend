package kr.io.pacer.core.controller;

import kr.io.pacer.core.auth.JwtFilter;
import kr.io.pacer.core.config.SecurityConfig;
import kr.io.pacer.core.config.TestSecurityConfig;
import kr.io.pacer.core.dto.response.BusStopResponse;
import kr.io.pacer.core.exception.BusStopNotFoundException;
import kr.io.pacer.core.service.BusStopService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = BusStopController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtFilter.class}
        )
)
@Import(TestSecurityConfig.class)
@DisplayName("BusStopController 컨트롤러 테스트")
class BusStopControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean BusStopService busStopService;

    private BusStopResponse mockResponse() {
        return BusStopResponse.builder()
                .stopId("BS001")
                .name("강남역")
                .nodeNo("N001")
                .cityCode(11)
                .lat(37.4979)
                .lng(127.0276)
                .build();
    }

    // ── GET /nearby ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/bus-stops/nearby - 정상: 200 + 목록 반환")
    void getNearby_validParams_returns200WithList() throws Exception {
        given(busStopService.findNearby(anyDouble(), anyDouble(), anyDouble()))
                .willReturn(List.of(mockResponse()));

        mockMvc.perform(get("/api/v1/bus-stops/nearby")
                        .param("lat", "37.4979")
                        .param("lng", "127.0276"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].stopId").value("BS001"))
                .andExpect(jsonPath("$[0].name").value("강남역"));
    }

    @Test
    @DisplayName("GET /api/v1/bus-stops/nearby - 커스텀 반경: 200")
    void getNearby_customRadius_returns200() throws Exception {
        given(busStopService.findNearby(anyDouble(), anyDouble(), anyDouble()))
                .willReturn(List.of(mockResponse()));

        mockMvc.perform(get("/api/v1/bus-stops/nearby")
                        .param("lat", "37.4979")
                        .param("lng", "127.0276")
                        .param("radiusM", "300"))
                .andExpect(status().isOk());

        then(busStopService).should().findNearby(37.4979, 127.0276, 300.0);
    }

    @Test
    @DisplayName("GET /api/v1/bus-stops/nearby - 결과 없음: 200 + 빈 배열")
    void getNearby_noResults_returns200WithEmptyArray() throws Exception {
        given(busStopService.findNearby(anyDouble(), anyDouble(), anyDouble()))
                .willReturn(List.of());

        mockMvc.perform(get("/api/v1/bus-stops/nearby")
                        .param("lat", "37.4979")
                        .param("lng", "127.0276"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── GET /search ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/bus-stops/search - 정상: 200 + 목록 반환")
    void search_validKeyword_returns200WithList() throws Exception {
        given(busStopService.search("강남")).willReturn(List.of(mockResponse()));

        mockMvc.perform(get("/api/v1/bus-stops/search")
                        .param("keyword", "강남"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("강남역"));
    }

    @Test
    @DisplayName("GET /api/v1/bus-stops/search - 결과 없음: 200 + 빈 배열")
    void search_noMatch_returns200WithEmptyArray() throws Exception {
        given(busStopService.search(anyString())).willReturn(List.of());

        mockMvc.perform(get("/api/v1/bus-stops/search")
                        .param("keyword", "없는정류장"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/bus-stops/search - Service에 정확한 keyword 전달")
    void search_passesKeywordToService() throws Exception {
        given(busStopService.search("서울역")).willReturn(List.of());

        mockMvc.perform(get("/api/v1/bus-stops/search")
                        .param("keyword", "서울역"))
                .andExpect(status().isOk());

        then(busStopService).should().search("서울역");
    }

    // ── GET /{stopId} ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/bus-stops/{stopId} - 정상: 200 + 정류장 반환")
    void getById_exists_returns200WithBusStop() throws Exception {
        given(busStopService.findById("BS001")).willReturn(mockResponse());

        mockMvc.perform(get("/api/v1/bus-stops/{stopId}", "BS001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stopId").value("BS001"))
                .andExpect(jsonPath("$.name").value("강남역"))
                .andExpect(jsonPath("$.lat").value(37.4979))
                .andExpect(jsonPath("$.lng").value(127.0276));
    }

    @Test
    @DisplayName("GET /api/v1/bus-stops/{stopId} - 존재하지 않음: 404")
    void getById_notFound_returns404() throws Exception {
        given(busStopService.findById("INVALID"))
                .willThrow(new BusStopNotFoundException("존재하지 않는 정류장입니다."));

        mockMvc.perform(get("/api/v1/bus-stops/{stopId}", "INVALID"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BUS_STOP_NOT_FOUND"));
    }
}
