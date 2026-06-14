package kr.io.pacer.core.service;

import kr.io.pacer.core.client.AiRouteClient;
import kr.io.pacer.core.client.CitsSpatClient;
import kr.io.pacer.core.client.CitsSpatStateClient;
import kr.io.pacer.core.domain.RouteHistory;
import kr.io.pacer.core.domain.User;
import kr.io.pacer.core.domain.enums.SignalState;
import kr.io.pacer.core.dto.ai.AiRouteRequest;
import kr.io.pacer.core.dto.ai.AiRouteResponse;
import kr.io.pacer.core.dto.external.SpatResponse;
import kr.io.pacer.core.dto.external.SpatStateResponse;
import kr.io.pacer.core.dto.request.RouteRequest;
import kr.io.pacer.core.dto.response.RouteHistoryResponse;
import kr.io.pacer.core.dto.response.RouteResponse;
import kr.io.pacer.core.exception.RouteNotFoundException;
import kr.io.pacer.core.repository.jdbc.RouteRepository.CrosswalkInfo;
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
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
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
    @DisplayName("경로 탐색 - 횡단보도 신호 있을 때 횡단보도 기준 SignalCheckpoint 포함")
    void findRoute_withCrosswalk_includesCrosswalkSignalCheckpoints() {
        UUID userId = UUID.randomUUID();
        RouteRequest req = makeRouteRequest(37.5, 127.0, 37.51, 127.01);

        IntersectionInfo intersection = new IntersectionInfo(202, "경로 주변 교차로", 37.505, 127.005, 0.5);
        CrosswalkInfo crosswalk = crosswalk(9001L, 101, 37.506, 127.006, 0.5, "nt", 250.0);
        CachedRoute cached = new CachedRoute("polyline", 300, 500.0, List.of(intersection), List.of(crosswalk));

        SpatResponse spat = makeSpatResponse(10.0, null, null, null, null, null, null, null);

        given(routeGeometryService.fetchAll(req, userId)).willReturn(List.of(cached));
        given(citsSpatClient.fetchAll(List.of(101))).willReturn(Map.of(101, spat));
        given(citsSpatStateClient.fetchAll(anyList())).willReturn(Map.of(
                101, makeSpatStateResponse("permissive-Movement-Allowed", null, null, null, null, null, null, null)));
        given(signalCycleRepository.findByItstIds(anyList())).willReturn(Map.of());
        given(profileRepository.findByUserId(userId)).willReturn(Optional.empty());
        given(polylineEncoder.decode("polyline")).willReturn(List.of(new double[]{37.5, 127.0}, new double[]{37.51, 127.01}));
        given(aiRouteClient.selectRoute(any())).willReturn(makeAiRouteResponse("route_001"));
        given(userRepository.getReferenceById(userId)).willReturn(User.of("user", "u@t.com", null));
        given(historyRepository.save(any())).willReturn(null);

        RouteResponse response = routeService.findRoute(req, userId);

        assertThat(response.getSignalCheckpoints()).hasSize(1);
        RouteResponse.SignalCheckpoint checkpoint = response.getSignalCheckpoints().get(0);
        assertThat(checkpoint.getCrosswalkId()).isEqualTo("9001");
        assertThat(checkpoint.getIntersectionId()).isEqualTo(101);
        assertThat(checkpoint.getLat()).isEqualTo(37.506);
        assertThat(checkpoint.getLng()).isEqualTo(127.006);
        assertThat(checkpoint.getEtaFromStartSeconds()).isEqualTo(150); // 0.5 * 300
        assertThat(checkpoint.getSignalDirection()).isEqualTo("nt");
        assertThat(checkpoint.getRemainingSeconds()).isEqualTo(10.0);
        assertThat(checkpoint.getSignalState()).isEqualTo(SignalState.GREEN);

        assertThat(response.getIntersectionSignals()).hasSize(1);
        assertThat(response.getIntersectionSignals().get(0).getItstId()).isEqualTo(101);
        assertThat(response.getIntersectionSignals().get(0).getName()).isEqualTo("교차로 101");
        assertThat(response.getIntersectionSignals().get(0).getLat()).isEqualTo(37.506);
        assertThat(response.getIntersectionSignals().get(0).getLng()).isEqualTo(127.006);
        assertThat(response.getIntersectionSignals().get(0).getNtPdsgRmdrCs()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("경로 탐색 - 같은 교차로의 근접 중복 횡단보도는 한 신호 이벤트로 생성")
    void findRoute_nearbyCrosswalksWithSameIntersection_createSingleSignalEvent() {
        UUID userId = UUID.randomUUID();
        RouteRequest req = makeRouteRequest(37.5, 127.0, 37.51, 127.01);

        CachedRoute cached = new CachedRoute(
                "polyline",
                300,
                500.0,
                List.of(
                        new IntersectionInfo(22300, "경로 주변 교차로 1", 37.545, 126.844, 0.8),
                        new IntersectionInfo(2300, "경로 주변 교차로 2", 37.546, 126.845, 0.9)),
                List.of(
                        crosswalk(1323417845L, 2017, 37.5417, 126.8400, 0.2, "wt", 100.0),
                        crosswalk(550970973L, 2017, 37.5418, 126.8404, 0.3, "wt", 150.0)));
        SpatResponse spat = makeSpatResponse(null, null, null, 15.0, null, null, null, null);

        given(routeGeometryService.fetchAll(req, userId)).willReturn(List.of(cached));
        given(citsSpatClient.fetchAll(List.of(2017))).willReturn(Map.of(2017, spat));
        given(citsSpatStateClient.fetchAll(anyList())).willReturn(Map.of(
                2017, makeSpatStateResponse(null, null, null, "permissive-Movement-Allowed", null, null, null, null)));
        given(signalCycleRepository.findByItstIds(anyList())).willReturn(Map.of(
                2017, Map.of("wt", signalCycle(130.07, 103.88))));
        given(profileRepository.findByUserId(userId)).willReturn(Optional.empty());
        given(polylineEncoder.decode("polyline")).willReturn(List.of(new double[]{37.5, 127.0}, new double[]{37.51, 127.01}));
        given(aiRouteClient.selectRoute(any())).willReturn(makeAiRouteResponse("route_001"));
        given(userRepository.getReferenceById(userId)).willReturn(User.of("user", "u@t.com", null));
        given(historyRepository.save(any())).willReturn(null);

        RouteResponse response = routeService.findRoute(req, userId);

        assertThat(response.getSignalCheckpoints())
                .extracting(RouteResponse.SignalCheckpoint::getIntersectionId)
                .containsExactly(2017);
        assertThat(response.getSignalCheckpoints())
                .extracting(RouteResponse.SignalCheckpoint::getCrosswalkId)
                .containsExactly("1323417845");
        assertThat(response.getSignalCheckpoints())
                .extracting(RouteResponse.SignalCheckpoint::getSignalDirection)
                .containsExactly("wt");
        assertThat(response.getIntersectionSignals())
                .extracting(RouteResponse.IntersectionSignal::getItstId)
                .containsExactly(2017);
        assertThat(response.getIntersectionSignals())
                .extracting(RouteResponse.IntersectionSignal::getLat)
                .containsExactly(37.5417);
        assertThat(response.getIntersectionSignals())
                .extracting(RouteResponse.IntersectionSignal::getLng)
                .containsExactly(126.8400);
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

    @Test
    @DisplayName("AI 요청 - crosswalk_id는 OSM way ID, intersection_id는 C-ITS itst_id 사용")
    void findRoute_aiRequest_usesCrosswalkIdAndIntersectionIdSeparately() {
        UUID userId = UUID.randomUUID();
        RouteRequest req = makeRouteRequest(37.5, 127.0, 37.51, 127.01);
        CrosswalkInfo crosswalk = crosswalk(9001L, 101, 37.505, 127.005, 0.4, "nt", 120.0);
        CachedRoute cached = new CachedRoute("polyline", 200, 300.0, List.of(), List.of(crosswalk));
        SpatResponse spat = makeSpatResponse(10.0, null, null, null, null, null, null, null);

        given(routeGeometryService.fetchAll(req, userId)).willReturn(List.of(cached));
        given(citsSpatClient.fetchAll(List.of(101))).willReturn(Map.of(101, spat));
        given(citsSpatStateClient.fetchAll(List.of(101))).willReturn(Map.of(
                101, makeSpatStateResponse("permissive-Movement-Allowed", null, null, null, null, null, null, null)));
        given(signalCycleRepository.findByItstIds(List.of(101))).willReturn(Map.of(
                101, Map.of("nt", RouteResponse.SignalCycle.builder()
                        .redMaxSec(30.0)
                        .greenMaxSec(60.0)
                        .build())));
        given(profileRepository.findByUserId(userId)).willReturn(Optional.empty());
        given(polylineEncoder.decode("polyline")).willReturn(List.of(new double[]{37.5, 127.0}, new double[]{37.51, 127.01}));
        given(aiRouteClient.selectRoute(any())).willReturn(makeAiRouteResponse("route_001"));
        given(userRepository.getReferenceById(userId)).willReturn(User.of("user", "u@t.com", null));
        given(historyRepository.save(any())).willReturn(null);

        routeService.findRoute(req, userId);

        ArgumentCaptor<AiRouteRequest> captor = ArgumentCaptor.forClass(AiRouteRequest.class);
        then(aiRouteClient).should().selectRoute(captor.capture());
        AiRouteRequest.Crosswalk payload = captor.getValue().getRouteCandidates().get(0).getCrosswalks().get(0);

        assertThat(payload.getCrosswalkId()).isEqualTo("9001");
        assertThat(payload.getIntersectionId()).isEqualTo(101);
        assertThat(payload.getDistanceFromStart()).isCloseTo(120.0, within(0.001));
        assertThat(payload.getSignal()).isNotNull();
        assertThat(payload.getSignal().getPhase()).isEqualTo("green");
        assertThat(payload.getSignal().getRemainingSeconds()).isEqualTo(10.0);
        assertThat(payload.getSignal().getCycleSeconds()).isEqualTo(90.0);
        then(citsSpatClient).should().fetchAll(List.of(101));
    }

    @Test
    @DisplayName("AI 요청 - 선택된 횡단보도 방향의 잔여시간과 주기만 사용")
    void findRoute_aiRequest_usesSelectedCrosswalkSignalDirection() {
        UUID userId = UUID.randomUUID();
        RouteRequest req = makeRouteRequest(37.5, 127.0, 37.51, 127.01);
        CrosswalkInfo crosswalk = crosswalk(9001L, 101, 37.505, 127.005, 0.4, "st", 120.0);
        CachedRoute cached = new CachedRoute("polyline", 200, 300.0, List.of(), List.of(crosswalk));
        SpatResponse spat = makeSpatResponse(10.0, null, 25.0, null, null, null, null, null);

        given(routeGeometryService.fetchAll(req, userId)).willReturn(List.of(cached));
        given(citsSpatClient.fetchAll(List.of(101))).willReturn(Map.of(101, spat));
        given(citsSpatStateClient.fetchAll(List.of(101))).willReturn(Map.of(
                101, makeSpatStateResponse("permissive-Movement-Allowed", null, "permissive-Movement-Allowed", null,
                        null, null, null, null)));
        given(signalCycleRepository.findByItstIds(List.of(101))).willReturn(Map.of(
                101, Map.of(
                        "nt", RouteResponse.SignalCycle.builder()
                                .redMaxSec(30.0)
                                .greenMaxSec(60.0)
                                .build(),
                        "st", RouteResponse.SignalCycle.builder()
                                .redMaxSec(40.0)
                                .greenMaxSec(80.0)
                                .build())));
        given(profileRepository.findByUserId(userId)).willReturn(Optional.empty());
        given(polylineEncoder.decode("polyline")).willReturn(List.of(new double[]{37.5, 127.0}, new double[]{37.51, 127.01}));
        given(aiRouteClient.selectRoute(any())).willReturn(makeAiRouteResponse("route_001"));
        given(userRepository.getReferenceById(userId)).willReturn(User.of("user", "u@t.com", null));
        given(historyRepository.save(any())).willReturn(null);

        routeService.findRoute(req, userId);

        ArgumentCaptor<AiRouteRequest> captor = ArgumentCaptor.forClass(AiRouteRequest.class);
        then(aiRouteClient).should().selectRoute(captor.capture());
        AiRouteRequest.Crosswalk payload = captor.getValue().getRouteCandidates().get(0).getCrosswalks().get(0);

        assertThat(payload.getSignal()).isNotNull();
        assertThat(payload.getSignal().getPhase()).isEqualTo("green");
        assertThat(payload.getSignal().getRemainingSeconds()).isEqualTo(25.0);
        assertThat(payload.getSignal().getCycleSeconds()).isEqualTo(120.0);
    }

    @Test
    @DisplayName("AI 요청·응답 - 가까운 같은 교차로·같은 방향 횡단 신호는 한 번만 사용")
    void findRoute_deduplicatesNearbySameIntersectionAndDirectionSignalEvents() {
        UUID userId = UUID.randomUUID();
        RouteRequest req = makeRouteRequest(37.5, 127.0, 37.51, 127.01);
        CrosswalkInfo firstNt = crosswalk(9001L, 101, 37.505, 127.005, 0.40, "nt", 120.0);
        CrosswalkInfo duplicateNt = crosswalk(9002L, 101, 37.506, 127.006, 0.43, "nt", 128.0);
        CrosswalkInfo differentDirection = crosswalk(9003L, 101, 37.507, 127.007, 0.50, "et", 160.0);
        CachedRoute cached = new CachedRoute("polyline", 200, 300.0, List.of(), List.of(
                firstNt, duplicateNt, differentDirection));
        SpatResponse spat = makeSpatResponse(10.0, 20.0, null, null, null, null, null, null);

        given(routeGeometryService.fetchAll(req, userId)).willReturn(List.of(cached));
        given(citsSpatClient.fetchAll(List.of(101))).willReturn(Map.of(101, spat));
        given(citsSpatStateClient.fetchAll(List.of(101))).willReturn(Map.of(
                101, makeSpatStateResponse("permissive-Movement-Allowed", "permissive-Movement-Allowed",
                        null, null, null, null, null, null)));
        given(signalCycleRepository.findByItstIds(List.of(101))).willReturn(Map.of(
                101, Map.of(
                        "nt", signalCycle(30.0, 60.0),
                        "et", signalCycle(40.0, 80.0))));
        given(profileRepository.findByUserId(userId)).willReturn(Optional.empty());
        given(polylineEncoder.decode("polyline")).willReturn(List.of(new double[]{37.5, 127.0}, new double[]{37.51, 127.01}));
        given(aiRouteClient.selectRoute(any())).willReturn(makeAiRouteResponse("route_001"));
        given(userRepository.getReferenceById(userId)).willReturn(User.of("user", "u@t.com", null));
        given(historyRepository.save(any())).willReturn(null);

        RouteResponse response = routeService.findRoute(req, userId);

        ArgumentCaptor<AiRouteRequest> captor = ArgumentCaptor.forClass(AiRouteRequest.class);
        then(aiRouteClient).should().selectRoute(captor.capture());
        List<AiRouteRequest.Crosswalk> payload = captor.getValue().getRouteCandidates().get(0).getCrosswalks();
        assertThat(payload).extracting(AiRouteRequest.Crosswalk::getCrosswalkId)
                .containsExactly("9001", "9003");
        assertThat(payload).extracting(c -> c.getSignal().getRemainingSeconds())
                .containsExactly(10.0, 20.0);

        assertThat(response.getSignalCheckpoints()).extracting(RouteResponse.SignalCheckpoint::getCrosswalkId)
                .containsExactly("9001", "9003");
        assertThat(response.getSignalCheckpoints()).extracting(RouteResponse.SignalCheckpoint::getSignalDirection)
                .containsExactly("nt", "et");
    }

    @Test
    @DisplayName("AI 요청 - 선택 방향 상태값이 없으면 signal null")
    void findRoute_aiRequest_withoutSelectedDirectionStatus_hasNullSignal() {
        CrosswalkInfo crosswalk = crosswalk(9001L, 101, 37.505, 127.005, 0.4, "et", 120.0);
        SpatResponse spat = makeSpatResponse(null, 97.0, null, null, null, null, null, null);

        AiRouteRequest.Crosswalk payload = findRouteAndCaptureSingleCrosswalk(
                crosswalk,
                spat,
                makeSpatStateResponse(null, null, null, null, null, null, null, null),
                Map.of("et", signalCycle(606.98, 115.01)));

        assertThat(payload.getSignal()).isNull();
    }

    @Test
    @DisplayName("경로 탐색 - 선택 방향 신호가 없어도 횡단보도는 제외하지 않는다")
    void findRoute_withoutSelectedDirectionData_doesNotUseUnavailableDirection() {
        UUID userId = UUID.randomUUID();
        RouteRequest req = makeRouteRequest(37.5, 127.0, 37.51, 127.01);
        CrosswalkInfo crosswalk = crosswalk(9001L, 101, 37.505, 127.005, 0.4, "wt", 120.0);
        CachedRoute cached = new CachedRoute("polyline", 200, 300.0, List.of(), List.of(crosswalk));
        SpatResponse spat = makeSpatResponse(null, null, null, null, 36001.0, null, null, null);

        given(routeGeometryService.fetchAll(req, userId)).willReturn(List.of(cached));
        given(citsSpatClient.fetchAll(List.of(101))).willReturn(Map.of(101, spat));
        given(citsSpatStateClient.fetchAll(List.of(101))).willReturn(Map.of(
                101, makeSpatStateResponse(null, null, null, null, "stop-And-Remain", null, null, null)));
        given(signalCycleRepository.findByItstIds(List.of(101))).willReturn(Map.of(
                101, Map.of("ne", signalCycle(130.07, 103.88))));
        given(profileRepository.findByUserId(userId)).willReturn(Optional.empty());
        given(polylineEncoder.decode("polyline")).willReturn(List.of(new double[]{37.5, 127.0}, new double[]{37.51, 127.01}));
        given(aiRouteClient.selectRoute(any())).willReturn(makeAiRouteResponse("route_001"));
        given(userRepository.getReferenceById(userId)).willReturn(User.of("user", "u@t.com", null));
        given(historyRepository.save(any())).willReturn(null);

        RouteResponse response = routeService.findRoute(req, userId);

        assertThat(response.getSignalCheckpoints()).hasSize(1);
        RouteResponse.SignalCheckpoint checkpoint = response.getSignalCheckpoints().get(0);
        assertThat(checkpoint.getCrosswalkId()).isEqualTo("9001");
        assertThat(checkpoint.getIntersectionId()).isEqualTo(101);
        assertThat(checkpoint.getSignalDirection()).isNull();
        assertThat(checkpoint.getRemainingSeconds()).isNull();
        assertThat(checkpoint.getSignalState()).isEqualTo(SignalState.UNKNOWN);
        assertThat(response.getIntersectionSignals()).hasSize(1);

        ArgumentCaptor<AiRouteRequest> captor = ArgumentCaptor.forClass(AiRouteRequest.class);
        then(aiRouteClient).should().selectRoute(captor.capture());
        List<AiRouteRequest.Crosswalk> payload = captor.getValue().getRouteCandidates().get(0).getCrosswalks();
        assertThat(payload).hasSize(1);
        assertThat(payload.get(0).getCrosswalkId()).isEqualTo("9001");
        assertThat(payload.get(0).getIntersectionId()).isEqualTo(101);
        assertThat(payload.get(0).getSignal()).isNull();
    }

    @Test
    @DisplayName("경로 탐색 - C-ITS 장애로 방향을 확정할 수 없어도 횡단보도는 제외하지 않는다")
    void findRoute_citsFailureKeepsCrosswalkWithNullSignal() {
        UUID userId = UUID.randomUUID();
        RouteRequest req = makeRouteRequest(37.5, 127.0, 37.51, 127.01);
        CrosswalkInfo crosswalk = crosswalk(9001L, 101, 37.505, 127.005, 0.4, "wt", 120.0);
        CachedRoute cached = new CachedRoute("polyline", 200, 300.0, List.of(), List.of(crosswalk));

        given(routeGeometryService.fetchAll(req, userId)).willReturn(List.of(cached));
        given(citsSpatClient.fetchAll(List.of(101))).willReturn(Map.of());
        given(citsSpatStateClient.fetchAll(List.of(101))).willReturn(Map.of());
        given(signalCycleRepository.findByItstIds(List.of(101))).willReturn(Map.of());
        given(profileRepository.findByUserId(userId)).willReturn(Optional.empty());
        given(polylineEncoder.decode("polyline")).willReturn(List.of(new double[]{37.5, 127.0}, new double[]{37.51, 127.01}));
        given(aiRouteClient.selectRoute(any())).willReturn(makeAiRouteResponse("route_001"));
        given(userRepository.getReferenceById(userId)).willReturn(User.of("user", "u@t.com", null));
        given(historyRepository.save(any())).willReturn(null);

        RouteResponse response = routeService.findRoute(req, userId);

        assertThat(response.getSignalCheckpoints()).hasSize(1);
        RouteResponse.SignalCheckpoint checkpoint = response.getSignalCheckpoints().get(0);
        assertThat(checkpoint.getCrosswalkId()).isEqualTo("9001");
        assertThat(checkpoint.getIntersectionId()).isEqualTo(101);
        assertThat(checkpoint.getSignalDirection()).isNull();
        assertThat(checkpoint.getRemainingSeconds()).isNull();
        assertThat(checkpoint.getSignalState()).isEqualTo(SignalState.UNKNOWN);

        ArgumentCaptor<AiRouteRequest> captor = ArgumentCaptor.forClass(AiRouteRequest.class);
        then(aiRouteClient).should().selectRoute(captor.capture());
        List<AiRouteRequest.Crosswalk> payload = captor.getValue().getRouteCandidates().get(0).getCrosswalks();
        assertThat(payload).hasSize(1);
        assertThat(payload.get(0).getCrosswalkId()).isEqualTo("9001");
        assertThat(payload.get(0).getIntersectionId()).isEqualTo(101);
        assertThat(payload.get(0).getSignal()).isNull();
    }

    @Test
    @DisplayName("AI 요청 - 선택 방향 정지 상태는 red phase로 전달")
    void findRoute_aiRequest_redSelectedDirectionStatus_hasRedPhase() {
        CrosswalkInfo crosswalk = crosswalk(9001L, 101, 37.505, 127.005, 0.4, "et", 120.0);
        SpatResponse spat = makeSpatResponse(null, 97.0, null, null, null, null, null, null);

        AiRouteRequest.Crosswalk payload = findRouteAndCaptureSingleCrosswalk(
                crosswalk,
                spat,
                makeSpatStateResponse(null, "stop-And-Remain", null, null, null, null, null, null),
                Map.of("et", signalCycle(606.98, 115.01)));

        assertThat(payload.getSignal()).isNotNull();
        assertThat(payload.getSignal().getPhase()).isEqualTo("red");
        assertThat(payload.getSignal().getRemainingSeconds()).isEqualTo(97.0);
        assertThat(payload.getSignal().getCycleSeconds()).isEqualTo(721.99);
    }

    @Test
    @DisplayName("AI 요청 - 대각 방향은 매핑 가능한 인접 직교 방향이 하나면 보정")
    void findRoute_aiRequest_diagonalDirection_usesSingleUsableAdjacentDirection() {
        CrosswalkInfo crosswalk = crosswalk(9001L, 101, 37.505, 127.005, 0.4, "nw", 120.0);
        SpatResponse spat = makeSpatResponse(10.0, null, null, null, null, null, null, null);

        AiRouteRequest.Crosswalk payload = findRouteAndCaptureSingleCrosswalk(
                crosswalk,
                spat,
                makeSpatStateResponse("permissive-Movement-Allowed", null, null, null, null, null, null, null),
                Map.of(
                        "nt", signalCycle(30.0, 60.0),
                        "wt", signalCycle(40.0, 80.0)));

        assertThat(payload.getSignal()).isNotNull();
        assertThat(payload.getSignal().getPhase()).isEqualTo("green");
        assertThat(payload.getSignal().getRemainingSeconds()).isEqualTo(10.0);
        assertThat(payload.getSignal().getCycleSeconds()).isEqualTo(90.0);
    }

    @Test
    @DisplayName("AI 요청 - 대각 방향의 인접 직교 방향이 둘 다 유효하면 signal null")
    void findRoute_aiRequest_diagonalDirection_withAmbiguousAdjacentDirections_hasNullSignal() {
        CrosswalkInfo crosswalk = crosswalk(9001L, 101, 37.505, 127.005, 0.4, "nw", 120.0);
        SpatResponse spat = makeSpatResponse(10.0, null, null, 20.0, null, null, null, null);

        List<AiRouteRequest.Crosswalk> payload = findRouteAndCaptureCrosswalks(
                crosswalk,
                spat,
                makeSpatStateResponse(null, null, null, "permissive-Movement-Allowed",
                        null, null, null, null),
                Map.of(
                        "nt", signalCycle(30.0, 60.0),
                        "wt", signalCycle(40.0, 80.0)));

        assertThat(payload).hasSize(1);
        assertThat(payload.get(0).getSignal()).isNull();
    }

    @Test
    @DisplayName("AI 요청 - 36001 잔여시간은 대각 방향 보정 후보에서 제외")
    void findRoute_aiRequest_diagonalDirection_ignoresInvalidRemainingTime() {
        CrosswalkInfo crosswalk = crosswalk(9001L, 101, 37.505, 127.005, 0.4, "nw", 120.0);
        SpatResponse spat = makeSpatResponse(36001.0, null, null, 20.0, null, null, null, null);

        AiRouteRequest.Crosswalk payload = findRouteAndCaptureSingleCrosswalk(
                crosswalk,
                spat,
                makeSpatStateResponse(null, null, null, "permissive-Movement-Allowed",
                        null, null, null, null),
                Map.of(
                        "nt", signalCycle(30.0, 60.0),
                        "wt", signalCycle(40.0, 80.0)));

        assertThat(payload.getSignal()).isNotNull();
        assertThat(payload.getSignal().getPhase()).isEqualTo("green");
        assertThat(payload.getSignal().getRemainingSeconds()).isEqualTo(20.0);
        assertThat(payload.getSignal().getCycleSeconds()).isEqualTo(120.0);
    }

    @Test
    @DisplayName("AI 요청 - 신호 주기 정보가 없으면 signal null로 전달")
    void findRoute_aiRequest_withoutCycle_hasNullSignal() {
        UUID userId = UUID.randomUUID();
        RouteRequest req = makeRouteRequest(37.5, 127.0, 37.51, 127.01);
        CrosswalkInfo crosswalk = crosswalk(9001L, 101, 37.505, 127.005, 0.4, "nt", 120.0);
        CachedRoute cached = new CachedRoute("polyline", 200, 300.0, List.of(), List.of(crosswalk));
        SpatResponse spat = makeSpatResponse(10.0, null, null, null, null, null, null, null);

        given(routeGeometryService.fetchAll(req, userId)).willReturn(List.of(cached));
        given(citsSpatClient.fetchAll(List.of(101))).willReturn(Map.of(101, spat));
        given(citsSpatStateClient.fetchAll(List.of(101))).willReturn(Map.of(
                101, makeSpatStateResponse("permissive-Movement-Allowed", null, null, null, null, null, null, null)));
        given(signalCycleRepository.findByItstIds(List.of(101))).willReturn(Map.of());
        given(profileRepository.findByUserId(userId)).willReturn(Optional.empty());
        given(polylineEncoder.decode("polyline")).willReturn(List.of(new double[]{37.5, 127.0}, new double[]{37.51, 127.01}));
        given(aiRouteClient.selectRoute(any())).willReturn(makeAiRouteResponse("route_001"));
        given(userRepository.getReferenceById(userId)).willReturn(User.of("user", "u@t.com", null));
        given(historyRepository.save(any())).willReturn(null);

        routeService.findRoute(req, userId);

        ArgumentCaptor<AiRouteRequest> captor = ArgumentCaptor.forClass(AiRouteRequest.class);
        then(aiRouteClient).should().selectRoute(captor.capture());
        AiRouteRequest.Crosswalk payload = captor.getValue().getRouteCandidates().get(0).getCrosswalks().get(0);

        assertThat(payload.getSignal()).isNull();
    }

    @Test
    @DisplayName("AI 요청 - 잔여 신호 시간이 없으면 signal null로 전달")
    void findRoute_aiRequest_withoutRemainingSeconds_hasNullSignal() {
        UUID userId = UUID.randomUUID();
        RouteRequest req = makeRouteRequest(37.5, 127.0, 37.51, 127.01);
        CrosswalkInfo crosswalk = crosswalk(9001L, 101, 37.505, 127.005, 0.4, "nt", 120.0);
        CachedRoute cached = new CachedRoute("polyline", 200, 300.0, List.of(), List.of(crosswalk));
        SpatResponse spat = makeSpatResponse(null, null, null, null, null, null, null, null);

        given(routeGeometryService.fetchAll(req, userId)).willReturn(List.of(cached));
        given(citsSpatClient.fetchAll(List.of(101))).willReturn(Map.of(101, spat));
        given(citsSpatStateClient.fetchAll(List.of(101))).willReturn(Map.of(
                101, makeSpatStateResponse("permissive-Movement-Allowed", null, null, null, null, null, null, null)));
        given(signalCycleRepository.findByItstIds(List.of(101))).willReturn(Map.of(
                101, Map.of("nt", RouteResponse.SignalCycle.builder()
                        .redMaxSec(30.0)
                        .greenMaxSec(60.0)
                        .build())));
        given(profileRepository.findByUserId(userId)).willReturn(Optional.empty());
        given(polylineEncoder.decode("polyline")).willReturn(List.of(new double[]{37.5, 127.0}, new double[]{37.51, 127.01}));
        given(aiRouteClient.selectRoute(any())).willReturn(makeAiRouteResponse("route_001"));
        given(userRepository.getReferenceById(userId)).willReturn(User.of("user", "u@t.com", null));
        given(historyRepository.save(any())).willReturn(null);

        routeService.findRoute(req, userId);

        ArgumentCaptor<AiRouteRequest> captor = ArgumentCaptor.forClass(AiRouteRequest.class);
        then(aiRouteClient).should().selectRoute(captor.capture());
        AiRouteRequest.Crosswalk payload = captor.getValue().getRouteCandidates().get(0).getCrosswalks().get(0);

        assertThat(payload.getSignal()).isNull();
    }

    @Test
    @DisplayName("AI 요청 - nearest_itst_id 없는 횡단보도는 signal null로 전달")
    void findRoute_aiRequest_crosswalkWithoutIntersection_hasNullSignal() {
        UUID userId = UUID.randomUUID();
        RouteRequest req = makeRouteRequest(37.5, 127.0, 37.51, 127.01);
        CrosswalkInfo crosswalk = crosswalk(9002L, null, 37.505, 127.005, 0.4, null, 120.0);
        CachedRoute cached = new CachedRoute("polyline", 200, 300.0, List.of(), List.of(crosswalk));

        given(routeGeometryService.fetchAll(req, userId)).willReturn(List.of(cached));
        given(profileRepository.findByUserId(userId)).willReturn(Optional.empty());
        given(polylineEncoder.decode("polyline")).willReturn(List.of(new double[]{37.5, 127.0}, new double[]{37.51, 127.01}));
        given(aiRouteClient.selectRoute(any())).willReturn(makeAiRouteResponse("route_001"));
        given(userRepository.getReferenceById(userId)).willReturn(User.of("user", "u@t.com", null));
        given(historyRepository.save(any())).willReturn(null);

        RouteResponse response = routeService.findRoute(req, userId);

        ArgumentCaptor<AiRouteRequest> captor = ArgumentCaptor.forClass(AiRouteRequest.class);
        then(aiRouteClient).should().selectRoute(captor.capture());

        assertThat(response.getSignalCheckpoints()).hasSize(1);
        assertThat(response.getSignalCheckpoints().get(0).getCrosswalkId()).isEqualTo("9002");
        assertThat(response.getSignalCheckpoints().get(0).getIntersectionId()).isNull();
        assertThat(response.getSignalCheckpoints().get(0).getSignalState()).isEqualTo(SignalState.UNKNOWN);
        List<AiRouteRequest.Crosswalk> payload = captor.getValue().getRouteCandidates().get(0).getCrosswalks();
        assertThat(payload).hasSize(1);
        assertThat(payload.get(0).getCrosswalkId()).isEqualTo("9002");
        assertThat(payload.get(0).getIntersectionId()).isNull();
        assertThat(payload.get(0).getSignal()).isNull();
        then(citsSpatClient).should(never()).fetchAll(anyList());
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    private AiRouteRequest.Crosswalk findRouteAndCaptureSingleCrosswalk(
            CrosswalkInfo crosswalk,
            SpatResponse spat,
            SpatStateResponse state,
            Map<String, RouteResponse.SignalCycle> cycles) {
        return findRouteAndCaptureCrosswalks(crosswalk, spat, state, cycles).get(0);
    }

    private List<AiRouteRequest.Crosswalk> findRouteAndCaptureCrosswalks(
            CrosswalkInfo crosswalk,
            SpatResponse spat,
            SpatStateResponse state,
            Map<String, RouteResponse.SignalCycle> cycles) {
        UUID userId = UUID.randomUUID();
        RouteRequest req = makeRouteRequest(37.5, 127.0, 37.51, 127.01);
        CachedRoute cached = new CachedRoute("polyline", 200, 300.0, List.of(), List.of(crosswalk));

        given(routeGeometryService.fetchAll(req, userId)).willReturn(List.of(cached));
        given(citsSpatClient.fetchAll(List.of(101))).willReturn(Map.of(101, spat));
        given(citsSpatStateClient.fetchAll(List.of(101))).willReturn(Map.of(101, state));
        given(signalCycleRepository.findByItstIds(List.of(101))).willReturn(Map.of(101, cycles));
        given(profileRepository.findByUserId(userId)).willReturn(Optional.empty());
        given(polylineEncoder.decode("polyline")).willReturn(List.of(new double[]{37.5, 127.0}, new double[]{37.51, 127.01}));
        given(aiRouteClient.selectRoute(any())).willReturn(makeAiRouteResponse("route_001"));
        given(userRepository.getReferenceById(userId)).willReturn(User.of("user", "u@t.com", null));
        given(historyRepository.save(any())).willReturn(null);

        routeService.findRoute(req, userId);

        ArgumentCaptor<AiRouteRequest> captor = ArgumentCaptor.forClass(AiRouteRequest.class);
        then(aiRouteClient).should().selectRoute(captor.capture());
        return captor.getValue().getRouteCandidates().get(0).getCrosswalks();
    }

    private CrosswalkInfo crosswalk(
            long crosswalkId,
            Integer itstId,
            double lat,
            double lng,
            double fraction,
            String signalDirection,
            double distanceFromStart) {
        return new CrosswalkInfo(
                crosswalkId,
                itstId,
                itstId == null ? null : "교차로 " + itstId,
                lat,
                lng,
                fraction,
                signalDirection,
                distanceFromStart);
    }

    private RouteResponse.SignalCycle signalCycle(Double redMaxSec, Double greenMaxSec) {
        return RouteResponse.SignalCycle.builder()
                .redMaxSec(redMaxSec)
                .greenMaxSec(greenMaxSec)
                .build();
    }

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

    private SpatStateResponse makeSpatStateResponse(String nt, String et, String st, String wt,
                                                    String ne, String se, String sw, String nw) {
        SpatStateResponse state = new SpatStateResponse();
        ReflectionTestUtils.setField(state, "ntPdsgStatNm", nt);
        ReflectionTestUtils.setField(state, "etPdsgStatNm", et);
        ReflectionTestUtils.setField(state, "stPdsgStatNm", st);
        ReflectionTestUtils.setField(state, "wtPdsgStatNm", wt);
        ReflectionTestUtils.setField(state, "nePdsgStatNm", ne);
        ReflectionTestUtils.setField(state, "sePdsgStatNm", se);
        ReflectionTestUtils.setField(state, "swPdsgStatNm", sw);
        ReflectionTestUtils.setField(state, "nwPdsgStatNm", nw);
        return state;
    }

    private AiRouteResponse makeAiRouteResponse(String optimalRouteId) {
        AiRouteResponse response = new AiRouteResponse();
        ReflectionTestUtils.setField(response, "optimalRouteId", optimalRouteId);
        return response;
    }
}
