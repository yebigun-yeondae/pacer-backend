package kr.io.pacer.core.service;

import kr.io.pacer.core.client.AiProfileClient;
import kr.io.pacer.core.domain.PedestrianProfile;
import kr.io.pacer.core.domain.User;
import kr.io.pacer.core.dto.ai.AiProfileUpdateRequest;
import kr.io.pacer.core.dto.ai.AiProfileUpdateResponse;
import kr.io.pacer.core.repository.jpa.PedestrianProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileAsyncService 단위 테스트")
class ProfileAsyncServiceTest {

    @InjectMocks ProfileAsyncService profileAsyncService;
    @Mock AiProfileClient aiProfileClient;
    @Mock PedestrianProfileRepository profileRepository;

    @Test
    @DisplayName("updated:true - AI가 준 avg_speed, speed_std, trip_count 모두 DB에 반영")
    void updateProfileAsync_updatedTrue_updatesAllFields() {
        UUID userId = UUID.randomUUID();
        User user = User.of("테스터", "test@test.com", null);
        PedestrianProfile profile = PedestrianProfile.createDefault(user);

        AiProfileUpdateRequest request = makeRequest(1.4, 0.2, 4, 1.6, 140.0);
        AiProfileUpdateResponse response = makeResponse(true, 1.44, 0.19, 5, null);

        given(aiProfileClient.updateProfile(request)).willReturn(response);
        given(profileRepository.findByUserId(userId)).willReturn(Optional.of(profile));

        profileAsyncService.updateProfileAsync(userId, request);

        assertThat(profile.getAvgSpeedMps()).isCloseTo(1.44, within(0.001));
        assertThat(profile.getSpeedStd()).isCloseTo(0.19, within(0.001));
        assertThat(profile.getTripCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("updated:false(warmup) - updated_profile 그대로 저장, avg_speed·speed_std 유지 trip_count +1")
    void updateProfileAsync_updatedFalse_warmup_onlyIncrementsTripCount() {
        UUID userId = UUID.randomUUID();
        User user = User.of("테스터", "test@test.com", null);
        PedestrianProfile profile = PedestrianProfile.createDefault(user);
        profile.updateFromAi(1.5, 0.18, 3);

        AiProfileUpdateRequest request = makeRequest(1.5, 0.18, 3, 1.6, 140.0);
        AiProfileUpdateResponse response = makeResponse(false, 1.5, 0.18, 4, "warmup_period");

        given(aiProfileClient.updateProfile(request)).willReturn(response);
        given(profileRepository.findByUserId(userId)).willReturn(Optional.of(profile));

        profileAsyncService.updateProfileAsync(userId, request);

        assertThat(profile.getTripCount()).isEqualTo(4);
        assertThat(profile.getAvgSpeedMps()).isCloseTo(1.5, within(0.001));
        assertThat(profile.getSpeedStd()).isCloseTo(0.18, within(0.001));
    }

    @Test
    @DisplayName("updated:false(actual_speed_out_of_range) - updated_profile 그대로 저장, 원본값 유지")
    void updateProfileAsync_updatedFalse_outOfRange_onlyIncrementsTripCount() {
        UUID userId = UUID.randomUUID();
        User user = User.of("테스터", "test@test.com", null);
        PedestrianProfile profile = PedestrianProfile.createDefault(user);
        profile.updateFromAi(1.4, 0.2, 5);

        AiProfileUpdateRequest request = makeRequest(1.4, 0.2, 5, 5.0, 100.0);
        AiProfileUpdateResponse response = makeResponse(false, 1.4, 0.2, 6, "actual_speed_out_of_range");

        given(aiProfileClient.updateProfile(request)).willReturn(response);
        given(profileRepository.findByUserId(userId)).willReturn(Optional.of(profile));

        profileAsyncService.updateProfileAsync(userId, request);

        assertThat(profile.getTripCount()).isEqualTo(6);
        assertThat(profile.getAvgSpeedMps()).isCloseTo(1.4, within(0.001));
    }

    @Test
    @DisplayName("AI 서버 422 에러 - 예외 삼키고 정상 종료")
    void updateProfileAsync_422Error_doesNotThrow() {
        UUID userId = UUID.randomUUID();
        AiProfileUpdateRequest request = makeRequest(1.4, 0.2, 0, 1.5, 100.0);

        given(aiProfileClient.updateProfile(request))
                .willThrow(HttpClientErrorException.UnprocessableEntity.class);

        // 예외가 외부로 전파되지 않아야 함
        profileAsyncService.updateProfileAsync(userId, request);

        then(profileRepository).should(never()).findByUserId(any());
    }

    @Test
    @DisplayName("AI 서버 기타 예외 - 예외 삼키고 정상 종료")
    void updateProfileAsync_genericException_doesNotThrow() {
        UUID userId = UUID.randomUUID();
        AiProfileUpdateRequest request = makeRequest(1.4, 0.2, 0, 1.5, 100.0);

        given(aiProfileClient.updateProfile(request))
                .willThrow(new RuntimeException("AI 서버 연결 실패"));

        profileAsyncService.updateProfileAsync(userId, request);

        then(profileRepository).should(never()).findByUserId(any());
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    private AiProfileUpdateRequest makeRequest(double avgSpeed, double speedStd, int tripCount,
                                                double actualAvgSpeed, double actualDurationSeconds) {
        return new AiProfileUpdateRequest(
                new AiProfileUpdateRequest.CurrentProfile(avgSpeed, speedStd, tripCount),
                actualAvgSpeed,
                actualDurationSeconds
        );
    }

    private AiProfileUpdateResponse makeResponse(boolean updated, double avgSpeed, double speedStd,
                                                  int tripCount, String skipReason) {
        AiProfileUpdateResponse response = new AiProfileUpdateResponse();
        AiProfileUpdateResponse.UpdatedProfile updatedProfile = new AiProfileUpdateResponse.UpdatedProfile();
        ReflectionTestUtils.setField(updatedProfile, "avgSpeed", avgSpeed);
        ReflectionTestUtils.setField(updatedProfile, "speedStd", speedStd);
        ReflectionTestUtils.setField(updatedProfile, "tripCount", tripCount);
        ReflectionTestUtils.setField(response, "updated", updated);
        ReflectionTestUtils.setField(response, "updatedProfile", updatedProfile);
        ReflectionTestUtils.setField(response, "skipReason", skipReason);
        return response;
    }
}
