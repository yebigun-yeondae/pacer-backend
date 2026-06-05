package kr.io.pacer.core.controller;

import kr.io.pacer.core.auth.JwtFilter;
import kr.io.pacer.core.config.SecurityConfig;
import kr.io.pacer.core.config.TestSecurityConfig;
import kr.io.pacer.core.dto.response.SignalResponse;
import kr.io.pacer.core.service.SignalService;
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

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = SignalController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtFilter.class}
        )
)
@Import(TestSecurityConfig.class)
@DisplayName("SignalController 컨트롤러 테스트")
class SignalControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean SignalService signalService;

    @Test
    @DisplayName("GET /api/v1/signal - 정상 단일 id: 200 + 신호 배열 반환")
    void getSignals_singleId_returns200() throws Exception {
        SignalResponse response = SignalResponse.builder()
                .itstId(101)
                .name("테스트 교차로")
                .ntPdsgRmdrCs(10.0)
                .stPdsgRmdrCs(5.0)
                .build();

        given(signalService.getSignals(List.of(101))).willReturn(List.of(response));

        mockMvc.perform(get("/api/v1/signal")
                        .param("itstIds", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].itstId").value(101))
                .andExpect(jsonPath("$[0].name").value("테스트 교차로"))
                .andExpect(jsonPath("$[0].ntPdsgRmdrCs").value(10.0));
    }

    @Test
    @DisplayName("GET /api/v1/signal - 복수 id: 200 + 복수 배열 반환")
    void getSignals_multipleIds_returnsAll() throws Exception {
        given(signalService.getSignals(List.of(101, 102))).willReturn(List.of(
                SignalResponse.builder().itstId(101).name("교차로A").build(),
                SignalResponse.builder().itstId(102).name("교차로B").build()
        ));

        mockMvc.perform(get("/api/v1/signal")
                        .param("itstIds", "101", "102"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].itstId").value(101))
                .andExpect(jsonPath("$[1].itstId").value(102));
    }

    @Test
    @DisplayName("GET /api/v1/signal - 신호 데이터 없는 교차로: 200 + null 필드 포함 반환")
    void getSignals_noSpatData_returnsNullFields() throws Exception {
        SignalResponse response = SignalResponse.builder()
                .itstId(999)
                .name("")
                .ntPdsgRmdrCs(null)
                .build();

        given(signalService.getSignals(List.of(999))).willReturn(List.of(response));

        mockMvc.perform(get("/api/v1/signal")
                        .param("itstIds", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].itstId").value(999))
                .andExpect(jsonPath("$[0].ntPdsgRmdrCs").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/signal - Service에 정확한 itstIds 전달")
    void getSignals_passesItstIdsToService() throws Exception {
        given(signalService.getSignals(List.of(101))).willReturn(List.of());

        mockMvc.perform(get("/api/v1/signal")
                        .param("itstIds", "101"))
                .andExpect(status().isOk());

        then(signalService).should().getSignals(List.of(101));
    }
}
