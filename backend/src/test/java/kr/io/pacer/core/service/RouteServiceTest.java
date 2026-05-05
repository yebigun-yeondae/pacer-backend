package kr.io.pacer.core.service;

import kr.io.pacer.core.client.CitsSpatClient;
import kr.io.pacer.core.client.ValhallaClient;
import kr.io.pacer.core.domain.PedestrianProfile;
import kr.io.pacer.core.domain.User;
import kr.io.pacer.core.domain.enums.SignalState;
import kr.io.pacer.core.dto.external.SpatResponse;
import kr.io.pacer.core.dto.external.ValhallaResponse;
import kr.io.pacer.core.dto.request.RouteRequest;
import kr.io.pacer.core.dto.response.RouteResponse;
import kr.io.pacer.core.exception.RouteNotFoundException;
import kr.io.pacer.core.repository.jdbc.RouteRepository;
import kr.io.pacer.core.repository.jdbc.RouteRepository.IntersectionInfo;
import kr.io.pacer.core.repository.jpa.PedestrianProfileRepository;
import kr.io.pacer.core.repository.jpa.RouteHistoryRepository;
import kr.io.pacer.core.repository.jpa.UserRepository;
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
    @Mock ValhallaClient valhallaClient;
    @Mock CitsSpatClient citsSpatClient;
    @Mock RouteRepository routeRepository;
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

        given(profileRepository.findByUserId(userId)).willReturn(Optional.empty());
        given(valhallaClient.route(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), anyDouble()))
                .willReturn(makeValhallaResponse("encodedPolyline", 300, 0.5));
        given(polylineEncoder.toWkt("encodedPolyline")).willReturn("LINESTRING(127.0 37.5, 127.01 37.51)");
        given(routeRepository.findIntersectionsByRouteWkt(any())).willReturn(List.of());
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
    @DisplayName("경로 탐색 - 유저 프로필 존재 시 개인 속도 Valhalla에 전달")
    void findRoute_withUserProfile_usesPersonalizedSpeed() {
        UUID userId = UUID.randomUUID();
        RouteRequest req = makeRouteRequest(37.5, 127.0, 37.51, 127.01);

        User user = User.of("user", "u@t.com", null);
        PedestrianProfile profile = PedestrianProfile.createDefault(user);
        profile.updateSpeed(2.0);

        given(profileRepository.findByUserId(userId)).willReturn(Optional.of(profile));
        given(valhallaClient.route(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                any(), eq(profile.getAvgSpeedMps())))
                .willReturn(makeValhallaResponse("polyline", 200, 0.4));
        given(polylineEncoder.toWkt("polyline")).willReturn("LINESTRING(127.0 37.5, 127.01 37.51)");
        given(routeRepository.findIntersectionsByRouteWkt(any())).willReturn(List.of());
        given(userRepository.getReferenceById(userId)).willReturn(user);
        given(historyRepository.save(any())).willReturn(null);

        routeService.findRoute(req, userId);

        then(valhallaClient).should()
                .route(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), eq(profile.getAvgSpeedMps()));
    }

    @Test
    @DisplayName("경로 탐색 - 교차로 신호 있을 때 SignalCheckpoint·IntersectionSignal 포함")
    void findRoute_withIntersection_includesSignalCheckpoints() {
        UUID userId = UUID.randomUUID();
        RouteRequest req = makeRouteRequest(37.5, 127.0, 37.51, 127.01);

        IntersectionInfo intersection = new IntersectionInfo(101, "테스트 교차로", 37.505, 127.005, 0.5);
        SpatResponse spat = makeSpatResponse(10, null, null, null, null, null, null, null); // 북쪽만 10초 남음

        given(profileRepository.findByUserId(userId)).willReturn(Optional.empty());
        given(valhallaClient.route(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), anyDouble()))
                .willReturn(makeValhallaResponse("polyline", 300, 0.5));
        given(polylineEncoder.toWkt("polyline")).willReturn("LINESTRING(127.0 37.5, 127.01 37.51)");
        given(routeRepository.findIntersectionsByRouteWkt(any())).willReturn(List.of(intersection));
        given(citsSpatClient.fetchAll(List.of(101))).willReturn(Map.of(101, spat));
        given(userRepository.getReferenceById(userId)).willReturn(User.of("user", "u@t.com", null));
        given(historyRepository.save(any())).willReturn(null);

        RouteResponse response = routeService.findRoute(req, userId);

        assertThat(response.getSignalCheckpoints()).hasSize(1);
        RouteResponse.SignalCheckpoint checkpoint = response.getSignalCheckpoints().get(0);
        assertThat(checkpoint.getNodeId()).isEqualTo(101L);
        assertThat(checkpoint.getEtaFromStartSeconds()).isEqualTo(150); // 0.5 * 300
        assertThat(checkpoint.getSignalState()).isEqualTo(SignalState.GREEN);

        assertThat(response.getIntersectionSignals()).hasSize(1);
    }

    // ── 경로 탐색 실패 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("경로 탐색 - Valhalla 실패: RouteNotFoundException 전파")
    void findRoute_valhallaFails_throwsRouteNotFoundException() {
        UUID userId = UUID.randomUUID();
        RouteRequest req = makeRouteRequest(37.5, 127.0, 37.51, 127.01);

        given(profileRepository.findByUserId(userId)).willReturn(Optional.empty());
        given(valhallaClient.route(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), anyDouble()))
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

    private ValhallaResponse makeValhallaResponse(String shape, double timeSec, double lengthKm) {
        ValhallaResponse response = new ValhallaResponse();
        ValhallaResponse.Summary summary = new ValhallaResponse.Summary();
        ReflectionTestUtils.setField(summary, "time", timeSec);
        ReflectionTestUtils.setField(summary, "length", lengthKm);

        ValhallaResponse.Leg leg = new ValhallaResponse.Leg();
        ReflectionTestUtils.setField(leg, "shape", shape);
        ReflectionTestUtils.setField(leg, "summary", summary);

        ValhallaResponse.Trip trip = new ValhallaResponse.Trip();
        ReflectionTestUtils.setField(trip, "legs", List.of(leg));
        ReflectionTestUtils.setField(trip, "summary", summary);

        ReflectionTestUtils.setField(response, "trip", trip);
        return response;
    }

    private SpatResponse makeSpatResponse(Integer nt, Integer et, Integer st, Integer wt,
                                          Integer ne, Integer se, Integer sw, Integer nw) {
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
