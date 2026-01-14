package basakan.fryday.scheduler;

import basakan.fryday.domain.user.User;
import basakan.fryday.repository.auth.UserJpaRepository;
import basakan.fryday.service.todo.RecurrenceOccurrenceMaterializeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 반복 투두 가상 회차를 실제 DB에 기록하는 스케줄러
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecurrenceOccurrenceMaterializeScheduler {

    private final UserJpaRepository userJpaRepository;
    private final RecurrenceOccurrenceMaterializeService materializeService;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void materializeTodayRecurrenceOccurrences() {
        LocalDate today = LocalDate.now();
        log.info("Recurrence Occurrence Materialization start: {}", today);

        List<User> users = userJpaRepository.findAll();

        int successCount = 0;
        int failCount = 0;

        for (User user : users) {
            try {
                materializeService.materializeTodayOccurrences(user.getId(), today);
                successCount++;
            } catch (Exception e) {
                log.error("반복 투두 생성 실패 - userId: {}, date: {}", user.getId(), today, e);
                failCount++;
            }
        }

        log.info("Recurrence Occurrence Materialization end - 성공: {}, 실패: {}", successCount, failCount);
    }
}