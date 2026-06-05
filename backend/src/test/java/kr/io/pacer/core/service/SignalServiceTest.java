package kr.io.pacer.core.service;

import kr.io.pacer.core.client.CitsSpatClient;
import kr.io.pacer.core.client.CitsSpatStateClient;
import kr.io.pacer.core.dto.external.SpatResponse;
import kr.io.pacer.core.dto.external.SpatStateResponse;
import kr.io.pacer.core.dto.response.SignalResponse;
import kr.io.pacer.core.repository.jdbc.SignalCycleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SignalService 단위 테스트")
class SignalServiceTest {

    @InjectMocks SignalService signalService;
    @Mock CitsSpatClient citsSpatClient;
    @Mock CitsSpatStateClient citsSpatStateClient;
    @Mock SignalCycleRepository signalCycleRepository;
    @Mock JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("getSignals - spat·state 데이터 있음: 모든 필드 매핑")
    @SuppressWarnings("unchecked")
    void getSignals_withSpatAndState_mapsAllFields() {
        SpatResponse spat = makeSpatResponse(10.0, 5.0);
        SpatStateResponse state = makeSpatStateResponse("GREEN", "RED");

        given(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class)))
                .willReturn(List.of(Map.entry(101, "테스트 교차로")));
        given(citsSpatClient.fetchAll(List.of(101))).willReturn(Map.of(101, spat));
        given(citsSpatStateClient.fetchAll(List.of(101))).willReturn(Map.of(101, state));
        given(signalCycleRepository.findByItstIds(List.of(101))).willReturn(Map.of());

        List<SignalResponse> result = signalService.getSignals(List.of(101));

        assertThat(result).hasSize(1);
        SignalResponse response = result.get(0);
        assertThat(response.getItstId()).isEqualTo(101);
        assertThat(response.getName()).isEqualTo("테스트 교차로");
        assertThat(response.getNtPdsgRmdrCs()).isEqualTo(10.0);
        assertThat(response.getStPdsgRmdrCs()).isEqualTo(5.0);
        assertThat(response.getNtPdsgStatNm()).isEqualTo("GREEN");
        assertThat(response.getStPdsgStatNm()).isEqualTo("RED");
    }

    @Test
    @DisplayName("getSignals - spat 데이터 없는 교차로: 신호 필드 null")
    @SuppressWarnings("unchecked")
    void getSignals_noSpatData_returnsNullSignalFields() {
        given(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class)))
                .willReturn(List.of(Map.entry(101, "교차로")));
        given(citsSpatClient.fetchAll(List.of(101))).willReturn(Map.of());
        given(citsSpatStateClient.fetchAll(List.of(101))).willReturn(Map.of());
        given(signalCycleRepository.findByItstIds(List.of(101))).willReturn(Map.of());

        List<SignalResponse> result = signalService.getSignals(List.of(101));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNtPdsgRmdrCs()).isNull();
        assertThat(result.get(0).getNtPdsgStatNm()).isNull();
    }

    @Test
    @DisplayName("getSignals - 교차로 이름 없음: name 빈 문자열")
    @SuppressWarnings("unchecked")
    void getSignals_noNameInDb_returnsEmptyName() {
        given(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class)))
                .willReturn(List.of());
        given(citsSpatClient.fetchAll(List.of(101))).willReturn(Map.of());
        given(citsSpatStateClient.fetchAll(List.of(101))).willReturn(Map.of());
        given(signalCycleRepository.findByItstIds(List.of(101))).willReturn(Map.of());

        List<SignalResponse> result = signalService.getSignals(List.of(101));

        assertThat(result.get(0).getName()).isEqualTo("");
    }

    @Test
    @DisplayName("getSignals - 복수 id: 각 id에 해당하는 신호 반환")
    @SuppressWarnings("unchecked")
    void getSignals_multipleIds_returnsOnePerId() {
        SpatResponse spat101 = makeSpatResponse(10.0, null);
        SpatResponse spat102 = makeSpatResponse(20.0, null);

        given(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class)))
                .willReturn(List.of(Map.entry(101, "교차로A"), Map.entry(102, "교차로B")));
        given(citsSpatClient.fetchAll(List.of(101, 102))).willReturn(Map.of(101, spat101, 102, spat102));
        given(citsSpatStateClient.fetchAll(List.of(101, 102))).willReturn(Map.of());
        given(signalCycleRepository.findByItstIds(List.of(101, 102))).willReturn(Map.of());

        List<SignalResponse> result = signalService.getSignals(List.of(101, 102));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getItstId()).isEqualTo(101);
        assertThat(result.get(1).getItstId()).isEqualTo(102);
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    private SpatResponse makeSpatResponse(Double nt, Double st) {
        SpatResponse spat = new SpatResponse();
        ReflectionTestUtils.setField(spat, "itstId", "101");
        ReflectionTestUtils.setField(spat, "ntPdsgRmdrCs", nt);
        ReflectionTestUtils.setField(spat, "stPdsgRmdrCs", st);
        return spat;
    }

    private SpatStateResponse makeSpatStateResponse(String nt, String st) {
        SpatStateResponse state = new SpatStateResponse();
        ReflectionTestUtils.setField(state, "ntPdsgStatNm", nt);
        ReflectionTestUtils.setField(state, "stPdsgStatNm", st);
        return state;
    }
}
