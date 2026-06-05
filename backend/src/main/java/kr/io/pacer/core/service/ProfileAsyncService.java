package kr.io.pacer.core.service;

import kr.io.pacer.core.client.AiProfileClient;
import kr.io.pacer.core.domain.PedestrianProfile;
import kr.io.pacer.core.dto.ai.AiProfileUpdateRequest;
import kr.io.pacer.core.dto.ai.AiProfileUpdateResponse;
import kr.io.pacer.core.repository.jpa.PedestrianProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileAsyncService {

    private final AiProfileClient aiProfileClient;
    private final PedestrianProfileRepository profileRepository;

    @Async
    @Transactional
    public void updateProfileAsync(UUID userId, AiProfileUpdateRequest request) {
        try {
            AiProfileUpdateResponse response = aiProfileClient.updateProfile(request);
            PedestrianProfile profile = profileRepository.findByUserId(userId)
                    .orElseThrow(() -> new IllegalStateException("프로필 없음: " + userId));

            AiProfileUpdateResponse.UpdatedProfile updated = response.getUpdatedProfile();
            profile.updateFromAi(updated.getAvgSpeed(), updated.getSpeedStd(), updated.getTripCount());
            log.info("[Profile] AI 프로필 저장 | userId={} updated={} skipReason={} avgSpeed={}m/s tripCount={}",
                    userId, response.isUpdated(), response.getSkipReason(), updated.getAvgSpeed(), updated.getTripCount());

        } catch (HttpClientErrorException.UnprocessableEntity e) {
            log.error("[Profile] AI 서버 422 에러로 프로필 업데이트 건너뜀 | userId={}", userId);
        } catch (Exception e) {
            log.error("[Profile] AI 프로필 업데이트 실패 | userId={} error={}", userId, e.getMessage());
        }
    }
}
