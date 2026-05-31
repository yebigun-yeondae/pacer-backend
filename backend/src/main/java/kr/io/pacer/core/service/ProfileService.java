package kr.io.pacer.core.service;

import kr.io.pacer.core.domain.PedestrianProfile;
import kr.io.pacer.core.domain.User;
import kr.io.pacer.core.dto.ai.AiProfileUpdateRequest;
import kr.io.pacer.core.dto.request.WalkingUpdateRequest;
import kr.io.pacer.core.dto.response.ProfileResponse;
import kr.io.pacer.core.repository.jpa.PedestrianProfileRepository;
import kr.io.pacer.core.repository.jpa.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final PedestrianProfileRepository profileRepository;
    private final ProfileAsyncService profileAsyncService;

    public ProfileResponse getProfile(UUID userId) {
        log.info("[Profile] 프로필 조회 | userId={}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("유저 없음"));
        PedestrianProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("프로필 없음"));

        return ProfileResponse.builder()
                .nickname(user.getNickname())
                .email(user.getEmail())
                .profileImageUrl(user.getProfileImageUrl())
                .avgSpeedMps(profile.getAvgSpeedMps())
                .totalRoutes(profile.getTotalRoutes())
                .totalDistanceM(profile.getTotalDistanceM())
                .build();
    }

    public void updateWalkingSpeed(UUID userId, WalkingUpdateRequest req) {
        double totalDistance = req.getSegments().stream()
                .mapToDouble(WalkingUpdateRequest.Segment::getDistanceM)
                .sum();
        double totalDuration = req.getSegments().stream()
                .mapToDouble(WalkingUpdateRequest.Segment::getDurationS)
                .sum();
        double actualAvgSpeed = totalDistance / totalDuration;

        PedestrianProfile profile = profileRepository.findByUserId(userId).orElse(null);
        AiProfileUpdateRequest.CurrentProfile currentProfile = new AiProfileUpdateRequest.CurrentProfile(
                profile != null ? profile.getAvgSpeedMps() : 1.4,
                profile != null ? profile.getSpeedStd()    : 0.2,
                profile != null ? profile.getTripCount()   : 0
        );

        AiProfileUpdateRequest aiRequest = new AiProfileUpdateRequest(currentProfile, actualAvgSpeed, totalDuration);
        profileAsyncService.updateProfileAsync(userId, aiRequest);
        log.info("[Profile] AI 보행속도 업데이트 비동기 시작 | userId={} speed={}m/s duration={}s",
                userId, actualAvgSpeed, totalDuration);
    }
}
