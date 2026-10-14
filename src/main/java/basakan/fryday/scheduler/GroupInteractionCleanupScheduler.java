package basakan.fryday.scheduler;

import basakan.fryday.repository.group.GroupInteractionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class GroupInteractionCleanupScheduler {

    private static final int RETENTION_YEARS = 1;

    private final GroupInteractionRepository groupInteractionRepository;

    @Scheduled(cron = "0 55 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void cleanupOldInteractions() {
        LocalDateTime threshold = LocalDate.now(ZoneId.of("Asia/Seoul")).minusYears(RETENTION_YEARS).atStartOfDay();
        int deleted = groupInteractionRepository.deleteAllByCreatedAtBefore(threshold);
        log.info("Group interaction cleanup: threshold={}, deleted={}", threshold, deleted);
    }
}
