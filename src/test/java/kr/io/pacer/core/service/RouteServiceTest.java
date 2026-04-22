package kr.io.pacer.core.service;

import kr.io.pacer.core.domain.*;
import kr.io.pacer.core.domain.enums.SignalState;
import kr.io.pacer.core.dto.internal.RouteSegment;
import kr.io.pacer.core.dto.request.RouteRequest;
import kr.io.pacer.core.dto.response.RouteResponse;
import kr.io.pacer.core.exception.RouteNotFoundException;
import kr.io.pacer.core.repository.*;
import kr.io.pacer.core.util.PolylineEncoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RouteService 단위 테스트")
class RouteServiceTest {

    @InjectMocks RouteService routeService;
    @Mock RouteRepository routeRepository;
    @Mock TrafficSignalRepository signalRepository;
    @Mock PedestrianProfileRepository profileRepository;
    @Mock UserRepository userRepository;
    @Mock RouteHistoryRepository historyRepository;
    @Mock PolylineEncoder polylineEncoder;

    // ── 경로 탐색 성공 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("경로 탐색 - 정상: polyline·시간·거리 반환, 기록 저장")
    void findRoute_success_returnsRouteResponseAndSavesHistory() {
        UUID userId = UUID.randomUUID();
        RouteRequest req = makeRouteRequest(37.5, 127.0, 37.51, 127.01);
        RouteSegment segment = makeSegment(1, 1L, 2L, 84.0, 60.0);

        given(profileRepository.findByUserId(userId)).willReturn(Optional.empty()); // 기본속도 1.4 사용
        given(routeRepository.findNearestNode(37.5, 127.0)).willReturn(1L);
        given(routeRepository.findNearestNode(37.51, 127.01)).willReturn(2L);
        given(routeRepository.findRoute(eq(1L), eq(2L), eq(1.4), anyDouble()))
                .willReturn(List.of(segment));
        given(polylineEncoder.encode(any())).willReturn("encodedPolyline");
        given(signalRepository.findByNodeIds(any())).willReturn(Map.of());
        given(userRepository.getReferenceById(userId))
                .willReturn(User.of("user", "u@t.com", null));
        given(historyRepository.save(any())).willReturn(mock(RouteHistory.class));

        RouteResponse response = routeService.findRoute(req, userId);

        assertThat(response.getPolyline()).isEqualTo("encodedPolyline");
        assertThat(response.getTotalTimeSeconds()).isEqualTo(60);
        assertThat(response.getTotalDistanceMeters()).isCloseTo(84.0, within(0.001));
        assertThat(response.getSignalCheckpoints()).isEmpty();
        then(historyRepository).should().save(any());
    }

    @Test
    @DisplayName("경로 탐색 - 유저 프로필 존재 시 개인 속도 사용")
    void findRoute_withUserProfile_usesPersonalizedSpeed() {
        UUID userId = UUID.randomUUID();
        RouteRequest req = makeRouteRequest(37.5, 127.0, 37.51, 127.01);

        User user = User.of("user", "u@t.com", null);
        PedestrianProfile profile = PedestrianProfile.createDefault(user);
        profile.updateSpeed(2.0); // avgSpeedMps ≠ 1.4

        given(profileRepository.findByUserId(userId)).willReturn(Optional.of(profile));
        given(routeRepository.findNearestNode(anyDouble(), anyDouble())).willReturn(1L);
        given(routeRepository.findRoute(anyLong(), anyLong(), eq(profile.getAvgSpeedMps()), anyDouble()))
                .willReturn(List.of(makeSegment(1, 1L, 2L, 100.0, 70.0)));
        given(polylineEncoder.encode(any())).willReturn("polyline");
        given(signalRepository.findByNodeIds(any())).willReturn(Map.of());
        given(userRepository.getReferenceById(userId)).willReturn(user);
        given(historyRepository.save(any())).willReturn(mock(RouteHistory.class));

        routeService.findRoute(req, userId);

        then(routeRepository).should().findRoute(anyLong(), anyLong(), eq(profile.getAvgSpeedMps()), anyDouble());
    }

    @Test
    @DisplayName("경로 탐색 - 신호 체크포인트 있을 때 GREEN/RED 상태 포함")
    void findRoute_withTrafficSignal_includesSignalCheckpoints() {
        UUID userId = UUID.randomUUID();
        RouteRequest req = makeRouteRequest(37.5, 127.0, 37.51, 127.01);
        RouteSegment segment = makeSegment(1, 1L, 99L, 84.0, 30.0); // targetNodeId=99

        TrafficSignal signal = buildSignal(99L, 30, 30, 0); // 30초에 도착 → 적색

        given(profileRepository.findByUserId(userId)).willReturn(Optional.empty());
        given(routeRepository.findNearestNode(anyDouble(), anyDouble())).willReturn(1L);
        given(routeRepository.findRoute(anyLong(), anyLong(), anyDouble(), anyDouble()))
                .willReturn(List.of(segment));
        given(polylineEncoder.encode(any())).willReturn("polyline");
        given(signalRepository.findByNodeIds(List.of(99L))).willReturn(Map.of(99L, signal));
        given(userRepository.getReferenceById(userId)).willReturn(User.of("user", "u@t.com", null));
        given(historyRepository.save(any())).willReturn(mock(RouteHistory.class));

        RouteResponse response = routeService.findRoute(req, userId);

        assertThat(response.getSignalCheckpoints()).hasSize(1);
        RouteResponse.SignalCheckpoint checkpoint = response.getSignalCheckpoints().get(0);
        assertThat(checkpoint.getNodeId()).isEqualTo(99L);
        // cumulativeSeconds=30, phase=30 → 적색
        assertThat(checkpoint.getSignalState()).isEqualTo(SignalState.RED);
    }

    // ── 경로 탐색 실패 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("경로 탐색 - 세그먼트 없음: RouteNotFoundException")
    void findRoute_emptySegments_throwsRouteNotFoundException() {
        UUID userId = UUID.randomUUID();
        RouteRequest req = makeRouteRequest(37.5, 127.0, 37.51, 127.01);

        given(profileRepository.findByUserId(userId)).willReturn(Optional.empty());
        given(routeRepository.findNearestNode(anyDouble(), anyDouble())).willReturn(1L);
        given(routeRepository.findRoute(anyLong(), anyLong(), anyDouble(), anyDouble()))
                .willReturn(List.of());

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

    private RouteSegment makeSegment(int seq, long edgeId, long targetNodeId,
                                     double lengthMeters, double cumulativeSeconds) {
        return RouteSegment.builder()
                .seq(seq)
                .edgeId(edgeId)
                .targetNodeId(targetNodeId)
                .costSeconds(cumulativeSeconds)
                .lengthMeters(lengthMeters)
                .endLat(37.51)
                .endLng(127.01)
                .cumulativeSeconds(cumulativeSeconds)
                .build();
    }

    private TrafficSignal buildSignal(long nodeId, int green, int red, int offset) {
        TrafficSignal signal = new TrafficSignal();
        ReflectionTestUtils.setField(signal, "nodeId", nodeId);
        ReflectionTestUtils.setField(signal, "greenDuration", green);
        ReflectionTestUtils.setField(signal, "redDuration", red);
        ReflectionTestUtils.setField(signal, "cycleOffset", offset);
        return signal;
    }
}
