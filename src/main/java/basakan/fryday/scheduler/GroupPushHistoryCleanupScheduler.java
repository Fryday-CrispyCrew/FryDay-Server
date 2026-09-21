package basakan.fryday.scheduler;

import basakan.fryday.repository.group.GroupPushHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class GroupPushHistoryCleanupScheduler {

    private static final int RETENTION_DAYS = 30;

    private final GroupPushHistoryRepository groupPushHistoryRepository;

    @Scheduled(cron = "0 50 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void cleanupOldHistory() {
        LocalDate threshold = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(RETENTION_DAYS);
        int deleted = groupPushHistoryRepository.deleteAllByPushDateBefore(threshold);
        log.info("Group push history cleanup: threshold={}, deleted={}", threshold, deleted);
    }
}
