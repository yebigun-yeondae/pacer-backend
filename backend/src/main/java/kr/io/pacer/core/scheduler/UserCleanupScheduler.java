package kr.io.pacer.core.scheduler;

import kr.io.pacer.core.domain.User;
import kr.io.pacer.core.repository.jpa.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserCleanupScheduler {

    private static final int WITHDRAWAL_RETENTION_DAYS = 365 * 5;

    private final UserRepository userRepository;
    private final FavoritePlaceRepository favoritePlaceRepository;
    private final RouteHistoryRepository routeHistoryRepository;
    private final PedestrianProfileRepository profileRepository;
    private final UserOAuthAccountRepository oauthAccountRepository;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void deleteWithdrawnUsers() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(WITHDRAWAL_RETENTION_DAYS);
        List<User> targets = userRepository.findByDeletedAtIsNotNullAndDeletedAtBefore(cutoff);

        if (targets.isEmpty()) {
            return;
        }

        for (User user : targets) {
            favoritePlaceRepository.deleteByUserId(user.getId());
            routeHistoryRepository.deleteByUserId(user.getId());
            profileRepository.deleteByUserId(user.getId());
            oauthAccountRepository.deleteByUserId(user.getId());
            userRepository.delete(user);
            log.info("[Cleanup] 탈퇴 계정 삭제 | userId={}", user.getId());
        }

        log.info("[Cleanup] 탈퇴 계정 {}개 삭제 완료", targets.size());
    }
}
