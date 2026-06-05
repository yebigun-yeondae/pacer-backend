package kr.io.pacer.core.service;

import kr.io.pacer.core.domain.PedestrianProfile;
import kr.io.pacer.core.domain.User;
import kr.io.pacer.core.dto.ai.AiProfileUpdateRequest;
import kr.io.pacer.core.dto.request.WalkingUpdateRequest;
import kr.io.pacer.core.dto.response.ProfileResponse;
import kr.io.pacer.core.repository.jpa.PedestrianProfileRepository;
import kr.io.pacer.core.repository.jpa.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileService 단위 테스트")
class ProfileServiceTest {

    @InjectMocks ProfileService profileService;
    @Mock UserRepository userRepository;
    @Mock PedestrianProfileRepository profileRepository;
    @Mock ProfileAsyncService profileAsyncService;

    // ── 프로필 조회 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("프로필 조회 - 정상: User·Profile 정보 올바르게 반환")
    void getProfile_returnsCorrectUserAndProfileData() {
        UUID userId = UUID.randomUUID();
        User user = User.of("테스터", "test@test.com", "http://img.com/1.jpg");
        PedestrianProfile profile = PedestrianProfile.createDefault(user);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(userId)).willReturn(Optional.of(profile));

        ProfileResponse response = profileService.getProfile(userId);

        assertThat(response.getNickname()).isEqualTo("테스터");
        assertThat(response.getEmail()).isEqualTo("test@test.com");
        assertThat(response.getProfileImageUrl()).isEqualTo("http://img.com/1.jpg");
        assertThat(response.getAvgSpeedMps()).isEqualTo(1.4);
        assertThat(response.getTotalRoutes()).isZero();
        assertThat(response.getTotalDistanceM()).isZero();
    }

    @Test
    @DisplayName("프로필 조회 - 유저 없음: IllegalStateException")
    void getProfile_userNotFound_throwsIllegalState() {
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getProfile(userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("유저 없음");
    }

    @Test
    @DisplayName("프로필 조회 - 프로필 없음: IllegalStateException")
    void getProfile_profileNotFound_throwsIllegalState() {
        UUID userId = UUID.randomUUID();
        User user = User.of("테스터", "test@test.com", null);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getProfile(userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("프로필 없음");
    }

    // ── 걸음속도 업데이트 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("걸음속도 업데이트 - segments 합산으로 actualAvgSpeed 정확히 계산")
    void updateWalkingSpeed_calculatesCorrectActualAvgSpeed() {
        UUID userId = UUID.randomUUID();
        User user = User.of("테스터", "test@test.com", null);
        PedestrianProfile profile = PedestrianProfile.createDefault(user);

        given(profileRepository.findByUserId(userId)).willReturn(Optional.of(profile));

        // totalDistance = 100 + 200 = 300m, totalDuration = 80 + 100 = 180s
        // actualAvgSpeed = 300 / 180 ≈ 1.6667 m/s
        WalkingUpdateRequest req = makeRequest(List.of(
                makeSegment(100.0, 80.0, 2.0),
                makeSegment(200.0, 100.0, -1.0)
        ));

        profileService.updateWalkingSpeed(userId, req);

        ArgumentCaptor<AiProfileUpdateRequest> captor = ArgumentCaptor.forClass(AiProfileUpdateRequest.class);
        then(profileAsyncService).should().updateProfileAsync(eq(userId), captor.capture());

        AiProfileUpdateRequest aiReq = captor.getValue();
        assertThat(aiReq.getActualDurationSeconds()).isCloseTo(180.0, within(0.001));
        assertThat(aiReq.getActualAvgSpeed()).isCloseTo(300.0 / 180.0, within(0.001));
    }

    @Test
    @DisplayName("걸음속도 업데이트 - 기존 프로필 있으면 현재값을 AI 요청에 포함")
    void updateWalkingSpeed_existingProfile_includesCurrentProfileInAiRequest() {
        UUID userId = UUID.randomUUID();
        User user = User.of("테스터", "test@test.com", null);
        PedestrianProfile profile = PedestrianProfile.createDefault(user);
        profile.updateFromAi(1.6, 0.15, 7);

        given(profileRepository.findByUserId(userId)).willReturn(Optional.of(profile));

        WalkingUpdateRequest req = makeRequest(List.of(makeSegment(100.0, 70.0, 0.0)));
        profileService.updateWalkingSpeed(userId, req);

        ArgumentCaptor<AiProfileUpdateRequest> captor = ArgumentCaptor.forClass(AiProfileUpdateRequest.class);
        then(profileAsyncService).should().updateProfileAsync(eq(userId), captor.capture());

        AiProfileUpdateRequest.CurrentProfile current = captor.getValue().getCurrentProfile();
        assertThat(current.getAvgSpeed()).isCloseTo(1.6, within(0.001));
        assertThat(current.getSpeedStd()).isCloseTo(0.15, within(0.001));
        assertThat(current.getTripCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("걸음속도 업데이트 - 프로필 없으면 기본값(1.4, 0.2, 0)으로 AI 요청")
    void updateWalkingSpeed_noProfile_usesDefaultValuesInAiRequest() {
        UUID userId = UUID.randomUUID();
        given(profileRepository.findByUserId(userId)).willReturn(Optional.empty());

        WalkingUpdateRequest req = makeRequest(List.of(makeSegment(100.0, 70.0, 0.0)));
        profileService.updateWalkingSpeed(userId, req);

        ArgumentCaptor<AiProfileUpdateRequest> captor = ArgumentCaptor.forClass(AiProfileUpdateRequest.class);
        then(profileAsyncService).should().updateProfileAsync(eq(userId), captor.capture());

        AiProfileUpdateRequest.CurrentProfile current = captor.getValue().getCurrentProfile();
        assertThat(current.getAvgSpeed()).isCloseTo(1.4, within(0.001));
        assertThat(current.getSpeedStd()).isCloseTo(0.2, within(0.001));
        assertThat(current.getTripCount()).isZero();
    }

    @Test
    @DisplayName("걸음속도 업데이트 - slopeDeg는 AI 요청에 포함되지 않음(경사도 제외)")
    void updateWalkingSpeed_slopeDegNotIncludedInAiRequest() {
        UUID userId = UUID.randomUUID();
        given(profileRepository.findByUserId(userId)).willReturn(Optional.empty());

        // 경사도 값이 어떻든 totalDistance/totalDuration만으로 speed 계산
        WalkingUpdateRequest req = makeRequest(List.of(
                makeSegment(120.0, 90.0, 30.0)  // 급경사
        ));
        profileService.updateWalkingSpeed(userId, req);

        ArgumentCaptor<AiProfileUpdateRequest> captor = ArgumentCaptor.forClass(AiProfileUpdateRequest.class);
        then(profileAsyncService).should().updateProfileAsync(eq(userId), captor.capture());

        // slope 보정 없이 순수 120/90 계산
        assertThat(captor.getValue().getActualAvgSpeed()).isCloseTo(120.0 / 90.0, within(0.001));
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    private WalkingUpdateRequest makeRequest(List<WalkingUpdateRequest.Segment> segments) {
        WalkingUpdateRequest req = new WalkingUpdateRequest();
        ReflectionTestUtils.setField(req, "segments", segments);
        return req;
    }

    private WalkingUpdateRequest.Segment makeSegment(double distM, double durS, double slopeDeg) {
        WalkingUpdateRequest.Segment seg = new WalkingUpdateRequest.Segment();
        ReflectionTestUtils.setField(seg, "distanceM", distM);
        ReflectionTestUtils.setField(seg, "durationS", durS);
        ReflectionTestUtils.setField(seg, "slopeDeg", slopeDeg);
        return seg;
    }
}
