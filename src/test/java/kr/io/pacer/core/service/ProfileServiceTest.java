package kr.io.pacer.core.service;

import kr.io.pacer.core.domain.PedestrianProfile;
import kr.io.pacer.core.domain.User;
import kr.io.pacer.core.dto.request.WalkingUpdateRequest;
import kr.io.pacer.core.dto.response.ProfileResponse;
import kr.io.pacer.core.repository.PedestrianProfileRepository;
import kr.io.pacer.core.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    @DisplayName("걸음속도 업데이트 - 기울기 보정 후 EMA로 속도 갱신")
    void updateWalkingSpeed_computesAvgWithSlopeCorrectionAndEma() {
        UUID userId = UUID.randomUUID();
        User user = User.of("테스터", "test@test.com", null);
        PedestrianProfile profile = PedestrianProfile.createDefault(user);

        given(profileRepository.findByUserId(userId)).willReturn(Optional.of(profile));

        // segment1: rawSpeed = 100/80 = 1.25, corrected = 1.25/(1-0*0.02) = 1.25
        // segment2: rawSpeed = 200/100 = 2.0,  corrected = 2.0/(1-0*0.02)  = 2.0
        // avgMeasured = (1.25 + 2.0) / 2 = 1.625
        // EMA: 1.4*0.7 + 1.625*0.3 = 0.98 + 0.4875 = 1.4675
        WalkingUpdateRequest req = makeRequest(List.of(
                makeSegment(100.0, 80.0, 0.0),
                makeSegment(200.0, 100.0, 0.0)
        ));

        profileService.updateWalkingSpeed(userId, req);

        assertThat(profile.getAvgSpeedMps()).isCloseTo(1.4675, within(0.0001));
    }

    @Test
    @DisplayName("걸음속도 업데이트 - 기울기가 있으면 보정된 속도 사용")
    void updateWalkingSpeed_withSlope_appliesSlopeCorrection() {
        UUID userId = UUID.randomUUID();
        User user = User.of("테스터", "test@test.com", null);
        PedestrianProfile profile = PedestrianProfile.createDefault(user);

        given(profileRepository.findByUserId(userId)).willReturn(Optional.of(profile));

        // rawSpeed = 100/100 = 1.0, corrected = 1.0 / (1 - 5 * 0.02) = 1.0 / 0.9 ≈ 1.1111
        WalkingUpdateRequest req = makeRequest(List.of(
                makeSegment(100.0, 100.0, 5.0)
        ));

        profileService.updateWalkingSpeed(userId, req);

        double expectedMeasured = 1.0 / (1 - 5 * 0.02);
        double expectedSpeed = 1.4 * 0.7 + expectedMeasured * 0.3;
        assertThat(profile.getAvgSpeedMps()).isCloseTo(expectedSpeed, within(0.0001));
    }

    @Test
    @DisplayName("걸음속도 업데이트 - 프로필 없음: IllegalStateException")
    void updateWalkingSpeed_profileNotFound_throwsIllegalState() {
        UUID userId = UUID.randomUUID();
        given(profileRepository.findByUserId(userId)).willReturn(Optional.empty());

        WalkingUpdateRequest req = makeRequest(List.of(makeSegment(100.0, 80.0, 0.0)));

        assertThatThrownBy(() -> profileService.updateWalkingSpeed(userId, req))
                .isInstanceOf(IllegalStateException.class);
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
