package kr.io.pacer.core.service;

import kr.io.pacer.core.domain.BusStop;
import kr.io.pacer.core.dto.response.BusStopResponse;
import kr.io.pacer.core.exception.BusStopNotFoundException;
import kr.io.pacer.core.repository.jpa.BusStopRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BusStopService 단위 테스트")
class BusStopServiceTest {

    @InjectMocks BusStopService busStopService;
    @Mock BusStopRepository busStopRepository;

    private static final GeometryFactory GF =
            new GeometryFactory(new PrecisionModel(), 4326);

    private BusStop makeBusStop(String stopId, String name) {
        Point point = GF.createPoint(new Coordinate(127.0276, 37.4979));
        return BusStop.of(stopId, name, "N001", 11, point);
    }

    // ── findNearby ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findNearby - 정상: Repository 결과를 DTO로 변환하여 반환")
    void findNearby_returnsConvertedDtos() {
        given(busStopRepository.findNearby(37.4979, 127.0276, 500.0))
                .willReturn(List.of(makeBusStop("BS001", "강남역")));

        List<BusStopResponse> result = busStopService.findNearby(37.4979, 127.0276, 500.0);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStopId()).isEqualTo("BS001");
        assertThat(result.get(0).getName()).isEqualTo("강남역");
        assertThat(result.get(0).getLat()).isEqualTo(37.4979);
        assertThat(result.get(0).getLng()).isEqualTo(127.0276);
    }

    @Test
    @DisplayName("findNearby - 반경 5000m 초과 시 5000m로 제한")
    void findNearby_radiusExceeds5000_capsAt5000() {
        given(busStopRepository.findNearby(anyDouble(), anyDouble(), eq(5000.0)))
                .willReturn(List.of());

        busStopService.findNearby(37.4979, 127.0276, 9999.0);

        then(busStopRepository).should().findNearby(37.4979, 127.0276, 5000.0);
    }

    @Test
    @DisplayName("findNearby - 결과 없음: 빈 리스트 반환")
    void findNearby_noResults_returnsEmptyList() {
        given(busStopRepository.findNearby(anyDouble(), anyDouble(), anyDouble()))
                .willReturn(List.of());

        List<BusStopResponse> result = busStopService.findNearby(37.4979, 127.0276, 500.0);

        assertThat(result).isEmpty();
    }

    // ── search ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("search - 키워드 매칭: Repository 결과를 DTO로 변환하여 반환")
    void search_matchingKeyword_returnsConvertedDtos() {
        given(busStopRepository.findByNameContainingIgnoreCase("강남"))
                .willReturn(List.of(
                        makeBusStop("BS001", "강남역"),
                        makeBusStop("BS002", "강남구청")
                ));

        List<BusStopResponse> result = busStopService.search("강남");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("강남역");
        assertThat(result.get(1).getName()).isEqualTo("강남구청");
    }

    @Test
    @DisplayName("search - 매칭 없음: 빈 리스트 반환")
    void search_noMatch_returnsEmptyList() {
        given(busStopRepository.findByNameContainingIgnoreCase("없는정류장"))
                .willReturn(List.of());

        List<BusStopResponse> result = busStopService.search("없는정류장");

        assertThat(result).isEmpty();
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById - 존재하는 id: DTO 변환하여 반환")
    void findById_exists_returnsDto() {
        given(busStopRepository.findById("BS001"))
                .willReturn(Optional.of(makeBusStop("BS001", "강남역")));

        BusStopResponse result = busStopService.findById("BS001");

        assertThat(result.getStopId()).isEqualTo("BS001");
        assertThat(result.getName()).isEqualTo("강남역");
    }

    @Test
    @DisplayName("findById - 존재하지 않는 id: BusStopNotFoundException")
    void findById_notExists_throwsBusStopNotFoundException() {
        given(busStopRepository.findById("INVALID")).willReturn(Optional.empty());

        assertThatThrownBy(() -> busStopService.findById("INVALID"))
                .isInstanceOf(BusStopNotFoundException.class);
    }
}
