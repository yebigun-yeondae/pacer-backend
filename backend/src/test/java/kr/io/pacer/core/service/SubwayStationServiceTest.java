package kr.io.pacer.core.service;

import kr.io.pacer.core.client.SubwayArrivalApiClient;
import kr.io.pacer.core.domain.SubwayStation;
import kr.io.pacer.core.dto.external.SubwayArrivalApiResponse.RealtimeArrival;
import kr.io.pacer.core.dto.response.SubwayArrivalResponse;
import kr.io.pacer.core.dto.response.SubwayStationResponse;
import kr.io.pacer.core.repository.jpa.SubwayStationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubwayStationService 단위 테스트")
class SubwayStationServiceTest {

    @InjectMocks SubwayStationService subwayStationService;
    @Mock SubwayStationRepository subwayStationRepository;
    @Mock SubwayArrivalApiClient subwayArrivalApiClient;

    private static final GeometryFactory GF =
            new GeometryFactory(new PrecisionModel(), 4326);

    private SubwayStation makeStation(String cd, String nm, String lineNum) {
        Point point = GF.createPoint(new Coordinate(127.0276, 37.4979));
        return SubwayStation.of(cd, nm, lineNum, null, null, point);
    }

    private RealtimeArrival makeArrival(String subwayId, String stationNm, String barvlDt) {
        try {
            RealtimeArrival a = new RealtimeArrival();
            setField(a, "subwayId", subwayId);
            setField(a, "statnNm", stationNm);
            setField(a, "trainLineNm", "2호선 외선순환");
            setField(a, "barvlDt", barvlDt);
            setField(a, "bstatnNm", "사당");
            setField(a, "arvlMsg2", "강남 도착");
            setField(a, "arvlMsg3", "[2]번째 칸");
            setField(a, "arvlCd", "1");
            setField(a, "lstcarAt", "0");
            return a;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setField(Object obj, String fieldName, String value) throws Exception {
        var field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }

    // ── findNearby ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findNearby - 정상: Repository 결과를 DTO로 변환하여 반환")
    void findNearby_returnsConvertedDtos() {
        given(subwayStationRepository.findNearby(37.4979, 127.0276, 500.0))
                .willReturn(List.of(makeStation("1001", "강남", "2호선")));

        List<SubwayStationResponse> result =
                subwayStationService.findNearby(37.4979, 127.0276, 500.0);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStationCd()).isEqualTo("1001");
        assertThat(result.get(0).getStationNm()).isEqualTo("강남");
        assertThat(result.get(0).getLineNum()).isEqualTo("2호선");
        assertThat(result.get(0).getLat()).isEqualTo(37.4979);
        assertThat(result.get(0).getLng()).isEqualTo(127.0276);
    }

    @Test
    @DisplayName("findNearby - 반경 5000m 초과 시 5000m로 제한")
    void findNearby_radiusExceeds5000_capsAt5000() {
        given(subwayStationRepository.findNearby(anyDouble(), anyDouble(), eq(5000.0)))
                .willReturn(List.of());

        subwayStationService.findNearby(37.4979, 127.0276, 9999.0);

        then(subwayStationRepository).should().findNearby(37.4979, 127.0276, 5000.0);
    }

    @Test
    @DisplayName("findNearby - 결과 없음: 빈 리스트 반환")
    void findNearby_noResults_returnsEmptyList() {
        given(subwayStationRepository.findNearby(anyDouble(), anyDouble(), anyDouble()))
                .willReturn(List.of());

        List<SubwayStationResponse> result =
                subwayStationService.findNearby(37.4979, 127.0276, 500.0);

        assertThat(result).isEmpty();
    }

    // ── findArrivals ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("findArrivals - 정상: 클라이언트 결과를 DTO로 변환하여 반환")
    void findArrivals_returnsConvertedDtos() {
        given(subwayArrivalApiClient.fetchArrivals("강남"))
                .willReturn(List.of(makeArrival("1002", "강남", "120")));

        List<SubwayArrivalResponse> result = subwayStationService.findArrivals("강남");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLineId()).isEqualTo("1002");
        assertThat(result.get(0).getStationNm()).isEqualTo("강남");
        assertThat(result.get(0).getRemainingSeconds()).isEqualTo(120);
        assertThat(result.get(0).isLastTrain()).isFalse();
    }

    @Test
    @DisplayName("findArrivals - barvlDt가 숫자가 아니면 remainingSeconds는 null")
    void findArrivals_invalidBarvlDt_remainingSecondsIsNull() {
        given(subwayArrivalApiClient.fetchArrivals("강남"))
                .willReturn(List.of(makeArrival("1002", "강남", "곧 도착")));

        List<SubwayArrivalResponse> result = subwayStationService.findArrivals("강남");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRemainingSeconds()).isNull();
    }

    @Test
    @DisplayName("findArrivals - 클라이언트가 빈 리스트 반환 시 빈 리스트")
    void findArrivals_clientReturnsEmpty_returnsEmptyList() {
        given(subwayArrivalApiClient.fetchArrivals("없는역"))
                .willReturn(List.of());

        List<SubwayArrivalResponse> result = subwayStationService.findArrivals("없는역");

        assertThat(result).isEmpty();
    }
}
