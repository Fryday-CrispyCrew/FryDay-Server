package basakan.fryday.scheduler;

import basakan.fryday.domain.user.User;
import basakan.fryday.repository.auth.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 탈퇴 계정 정리 스케줄러
 * - 매일 00:30에 실행
 * - 탈퇴 후 7일이 경과한 계정을 삭제
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WithdrawnAccountCleanupScheduler {

    private final UserJpaRepository userJpaRepository;

    @Scheduled(cron = "0 30 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void cleanupWithdrawnAccounts() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        log.info("Withdrawn account cleanup start: threshold={}", threshold);

        List<User> expiredUsers = userJpaRepository.findAllByAccountStatusAndWithdrawnAtBefore(
                User.AccountStatus.WITHDRAWN, threshold
        );

        if (expiredUsers.isEmpty()) {
            log.info("No expired withdrawn accounts found");
            return;
        }

        userJpaRepository.deleteAll(expiredUsers);

        log.info("Withdrawn account cleanup end: deleted={}", expiredUsers.size());
    }
}
