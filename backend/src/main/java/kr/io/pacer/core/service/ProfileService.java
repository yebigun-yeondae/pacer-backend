package kr.io.pacer.core.service;

import kr.io.pacer.core.domain.PedestrianProfile;
import kr.io.pacer.core.domain.User;
import kr.io.pacer.core.dto.request.WalkingUpdateRequest;
import kr.io.pacer.core.dto.response.ProfileResponse;
import kr.io.pacer.core.repository.jpa.PedestrianProfileRepository;
import kr.io.pacer.core.repository.jpa.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final PedestrianProfileRepository profileRepository;

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

    @Transactional
    public void updateWalkingSpeed(UUID userId, WalkingUpdateRequest req) {
        PedestrianProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("프로파일 없음"));

        double avgMeasured = req.getSegments().stream()
                .mapToDouble(s -> {
                    double rawSpeed = s.getDistanceM() / s.getDurationS();
                    return rawSpeed / (1 - s.getSlopeDeg() * 0.02);
                })
                .average()
                .orElse(1.4);

        profile.updateSpeed(avgMeasured);
        log.info("[Profile] 보행속도 업데이트 | userId={} newSpeed={}m/s", userId, avgMeasured);
    }
}
