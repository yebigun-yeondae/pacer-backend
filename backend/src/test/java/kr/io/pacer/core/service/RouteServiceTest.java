package kr.io.pacer.core.service;

import kr.io.pacer.core.client.AiRouteClient;
import kr.io.pacer.core.client.CitsSpatClient;
import kr.io.pacer.core.client.CitsSpatStateClient;
import kr.io.pacer.core.domain.User;
import kr.io.pacer.core.domain.enums.SignalState;
import kr.io.pacer.core.dto.external.SpatResponse;
import kr.io.pacer.core.dto.request.RouteRequest;
import kr.io.pacer.core.dto.response.RouteHistoryResponse;
import kr.io.pacer.core.dto.response.RouteResponse;
import kr.io.pacer.core.exception.RouteNotFoundException;
import kr.io.pacer.core.repository.jdbc.RouteRepository.IntersectionInfo;
import kr.io.pacer.core.repository.jdbc.SignalCycleRepository;
import kr.io.pacer.core.repository.jpa.PedestrianProfileRepository;
import kr.io.pacer.core.repository.jpa.RouteHistoryRepository;
import kr.io.pacer.core.repository.jpa.UserRepository;
import kr.io.pacer.core.service.RouteGeometryService.CachedRoute;
import kr.io.pacer.core.util.PolylineEncoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import kr.io.pacer.core.domain.RouteHistory;
import kr.io.pacer.core.dto.response.RouteHistoryResponse;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RouteService 단위 테스트")
class RouteServiceTest {

    @InjectMocks RouteService routeService;
    @Mock RouteGeometryService routeGeometryService;
    @Mock CitsSpatClient citsSpatClient;
    @Mock CitsSpatStateClient citsSpatStateClient;
    @Mock SignalCycleRepository signalCycleRepository;
    @Mock AiRouteClient aiRouteClient;
    @Mock PolylineEncoder polylineEncoder;
    @Mock PedestrianProfileRepository profileRepository;
    @Mock UserRepository userRepository;
    @Mock RouteHistoryRepository historyRepository;
    @Mock FavoritePlaceService favoritePlaceService;

    // ── 경로 탐색 성공 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("경로 탐색 - 정상: polyline·시간·거리 반환, 기록 저장")
    void findRoute_success_returnsRouteResponseAndSavesHistory() {
        UUID userId = UUID.randomUUID();
        RouteRequest req = makeRouteRequest(37.5, 127.0, 37.51, 127.01);
        CachedRoute cached = new CachedRoute("encodedPolyline", 300, 500.0, List.of(), List.of());

        given(routeGeometryService.fetchAll(req, userId)).willReturn(List.of(cached));
        given(userRepository.getReferenceById(userId)).willReturn(User.of("user", "u@t.com", null));
        given(historyRepository.save(any())).willReturn(null);

        RouteResponse response = routeService.findRoute(req, userId);

        assertThat(response.getPolyline()).isEqualTo("encodedPolyline");
        assertThat(response.getTotalTimeSeconds()).isEqualTo(300);
        assertThat(response.getTotalDistanceMeters()).isCloseTo(500.0, within(0.001));
        assertThat(response.getSignalCheckpoints()).isEmpty();
        then(historyRepository).should().save(any());
    }

    @Test
    @DisplayName("경로 탐색 - 교차로 신호 있을 때 SignalCheckpoint·IntersectionSignal 포함")
    void findRoute_withIntersection_includesSignalCheckpoints() {
        UUID userId = UUID.randomUUID();
        RouteRequest req = makeRouteRequest(37.5, 127.0, 37.51, 127.01);

        IntersectionInfo intersection = new IntersectionInfo(101, "테스트 교차로", 37.505, 127.005, 0.5);
        CachedRoute cached = new CachedRoute("polyline", 300, 500.0, List.of(intersection), List.of());

        SpatResponse spat = makeSpatResponse(10.0, null, null, null, null, null, null, null);

        given(routeGeometryService.fetchAll(req, userId)).willReturn(List.of(cached));
        given(citsSpatClient.fetchAll(List.of(101))).willReturn(Map.of(101, spat));
        given(citsSpatStateClient.fetchAll(anyList())).willReturn(Map.of());
        given(signalCycleRepository.findByItstIds(anyList())).willReturn(Map.of());
        given(userRepository.getReferenceById(userId)).willReturn(User.of("user", "u@t.com", null));
        given(historyRepository.save(any())).willReturn(null);

        RouteResponse response = routeService.findRoute(req, userId);

        assertThat(response.getSignalCheckpoints()).hasSize(1);
        RouteResponse.SignalCheckpoint checkpoint = response.getSignalCheckpoints().get(0);
        assertThat(checkpoint.getNodeId()).isEqualTo(101);
        assertThat(checkpoint.getEtaFromStartSeconds()).isEqualTo(150); // 0.5 * 300
        assertThat(checkpoint.getSignalState()).isEqualTo(SignalState.UNKNOWN);

        assertThat(response.getIntersectionSignals()).hasSize(1);
    }

    @Test
    @DisplayName("경로 탐색 - 유저 프로필 없음: 기본 속도 사용")
    void findRoute_noProfile_usesDefaultSpeed() {
        UUID userId = UUID.randomUUID();
        RouteRequest req = makeRouteRequest(37.5, 127.0, 37.51, 127.01);
        CachedRoute cached = new CachedRoute("polyline", 200, 300.0, List.of(), List.of());

        given(routeGeometryService.fetchAll(req, userId)).willReturn(List.of(cached));
        given(userRepository.getReferenceById(userId)).willReturn(User.of("user", "u@t.com", null));
        given(historyRepository.save(any())).willReturn(null);

        RouteResponse response = routeService.findRoute(req, userId);

        assertThat(response.getTotalTimeSeconds()).isEqualTo(200);
    }

    // ── 히스토리 조회 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("히스토리 조회 - Repository 결과를 DTO로 변환하여 반환")
    void getHistory_returnsConvertedDtos() {
        UUID userId = UUID.randomUUID();
        RouteHistory history = mock(RouteHistory.class);
        GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);
        given(history.getOriginGeom()).willReturn(gf.createPoint(new Coordinate(127.0, 37.5)));
        given(history.getDestinationGeom()).willReturn(gf.createPoint(new Coordinate(127.01, 37.51)));
        given(historyRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any()))
                .willReturn(List.of(history));

        List<RouteHistoryResponse> result = routeService.getHistory(userId, PageRequest.of(0, 20));

        assertThat(result).hasSize(1);
        then(historyRepository).should().findByUserIdOrderByCreatedAtDesc(eq(userId), any());
    }

    @Test
    @DisplayName("히스토리 조회 - 히스토리 없음: 빈 리스트 반환")
    void getHistory_noHistory_returnsEmptyList() {
        UUID userId = UUID.randomUUID();
        given(historyRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any()))
                .willReturn(List.of());

        List<RouteHistoryResponse> result = routeService.getHistory(userId, PageRequest.of(0, 20));

        assertThat(result).isEmpty();
    }

    // ── 경로 탐색 실패 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("경로 탐색 - 경로 없음: RouteNotFoundException 전파")
    void findRoute_routeNotFound_throwsRouteNotFoundException() {
        UUID userId = UUID.randomUUID();
        RouteRequest req = makeRouteRequest(37.5, 127.0, 37.51, 127.01);

        given(routeGeometryService.fetchAll(req, userId))
                .willThrow(new RouteNotFoundException("경로를 찾을 수 없습니다."));

        assertThatThrownBy(() -> routeService.findRoute(req, userId))
                .isInstanceOf(RouteNotFoundException.class);
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    private RouteRequest makeRouteRequest(double startLat, double startLng,
                                          double endLat, double endLng) {
        RouteRequest req = new RouteRequest();
        RouteRequest.Coordinate origin = new RouteRequest.Coordinate();
        RouteRequest.Coordinate dest   = new RouteRequest.Coordinate();
        ReflectionTestUtils.setField(origin, "lat", startLat);
        ReflectionTestUtils.setField(origin, "lng", startLng);
        ReflectionTestUtils.setField(dest, "lat", endLat);
        ReflectionTestUtils.setField(dest, "lng", endLng);
        ReflectionTestUtils.setField(req, "origin", origin);
        ReflectionTestUtils.setField(req, "destination", dest);
        return req;
    }

    private SpatResponse makeSpatResponse(Double nt, Double et, Double st, Double wt,
                                          Double ne, Double se, Double sw, Double nw) {
        SpatResponse spat = new SpatResponse();
        ReflectionTestUtils.setField(spat, "itstId", "101");
        ReflectionTestUtils.setField(spat, "ntPdsgRmdrCs", nt);
        ReflectionTestUtils.setField(spat, "etPdsgRmdrCs", et);
        ReflectionTestUtils.setField(spat, "stPdsgRmdrCs", st);
        ReflectionTestUtils.setField(spat, "wtPdsgRmdrCs", wt);
        ReflectionTestUtils.setField(spat, "nePdsgRmdrCs", ne);
        ReflectionTestUtils.setField(spat, "sePdsgRmdrCs", se);
        ReflectionTestUtils.setField(spat, "swPdsgRmdrCs", sw);
        ReflectionTestUtils.setField(spat, "nwPdsgRmdrCs", nw);
        return spat;
    }
}
