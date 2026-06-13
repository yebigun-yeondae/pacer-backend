package kr.io.pacer.core.controller;

import kr.io.pacer.core.auth.JwtFilter;
import kr.io.pacer.core.config.SecurityConfig;
import kr.io.pacer.core.config.TestSecurityConfig;
import kr.io.pacer.core.dto.response.SubwayArrivalResponse;
import kr.io.pacer.core.dto.response.SubwayStationResponse;
import kr.io.pacer.core.service.SubwayStationService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = SubwayStationController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtFilter.class}
        )
)
@Import(TestSecurityConfig.class)
@DisplayName("SubwayStationController 컨트롤러 테스트")
class SubwayStationControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean SubwayStationService subwayStationService;

    private SubwayStationResponse mockStation() {
        return SubwayStationResponse.builder()
                .stationCd("1001")
                .stationNm("강남")
                .lineNum("2호선")
                .lat(37.4979)
                .lng(127.0276)
                .build();
    }

    private SubwayArrivalResponse mockArrival() {
        return SubwayArrivalResponse.builder()
                .lineId("1002")
                .stationNm("강남")
                .trainLineNm("2호선 외선순환")
                .currentStation("사당")
                .remainingSeconds(120)
                .arrivalMessage("강남 도착")
                .positionMessage("[2]번째 칸")
                .arrivalCode("1")
                .isLastTrain(false)
                .build();
    }

    // ── GET /nearby ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /nearby - 정상: 200 + 역 목록 반환")
    void getNearby_validParams_returns200WithList() throws Exception {
        given(subwayStationService.findNearby(anyDouble(), anyDouble(), anyDouble()))
                .willReturn(List.of(mockStation()));

        mockMvc.perform(get("/api/v1/subway-stations/nearby")
                        .param("lat", "37.4979")
                        .param("lng", "127.0276"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].stationCd").value("1001"))
                .andExpect(jsonPath("$[0].stationNm").value("강남"))
                .andExpect(jsonPath("$[0].lineNum").value("2호선"))
                .andExpect(jsonPath("$[0].lat").value(37.4979))
                .andExpect(jsonPath("$[0].lng").value(127.0276));
    }

    @Test
    @DisplayName("GET /nearby - 커스텀 반경: Service에 정확한 값 전달")
    void getNearby_customRadius_passesValueToService() throws Exception {
        given(subwayStationService.findNearby(anyDouble(), anyDouble(), anyDouble()))
                .willReturn(List.of());

        mockMvc.perform(get("/api/v1/subway-stations/nearby")
                        .param("lat", "37.4979")
                        .param("lng", "127.0276")
                        .param("radiusM", "1000"))
                .andExpect(status().isOk());

        then(subwayStationService).should().findNearby(37.4979, 127.0276, 1000.0);
    }

    @Test
    @DisplayName("GET /nearby - 결과 없음: 200 + 빈 배열")
    void getNearby_noResults_returns200WithEmptyArray() throws Exception {
        given(subwayStationService.findNearby(anyDouble(), anyDouble(), anyDouble()))
                .willReturn(List.of());

        mockMvc.perform(get("/api/v1/subway-stations/nearby")
                        .param("lat", "37.4979")
                        .param("lng", "127.0276"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── GET /{stationNm}/arrivals ─────────────────────────────────────────────

    @Test
    @DisplayName("GET /{stationNm}/arrivals - 정상: 200 + 도착정보 반환")
    void getArrivals_validStation_returns200WithList() throws Exception {
        given(subwayStationService.findArrivals("강남"))
                .willReturn(List.of(mockArrival()));

        mockMvc.perform(get("/api/v1/subway-stations/{stationNm}/arrivals", "강남"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].lineId").value("1002"))
                .andExpect(jsonPath("$[0].stationNm").value("강남"))
                .andExpect(jsonPath("$[0].remainingSeconds").value(120))
                .andExpect(jsonPath("$[0].lastTrain").value(false));
    }

    @Test
    @DisplayName("GET /{stationNm}/arrivals - 도착 정보 없음: 200 + 빈 배열")
    void getArrivals_noArrivals_returns200WithEmptyArray() throws Exception {
        given(subwayStationService.findArrivals("없는역"))
                .willReturn(List.of());

        mockMvc.perform(get("/api/v1/subway-stations/{stationNm}/arrivals", "없는역"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
