package basakan.fryday.scheduler;

import basakan.fryday.domain.auth.User;
import basakan.fryday.repository.auth.UserJpaRepository;
import basakan.fryday.service.DailyResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyResultScheduler {

    private final UserJpaRepository userJpaRepository;
    private final DailyResultService dailyResultService;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void scheduleDailyResultRecording() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("Daily Result Recording start: {}", yesterday);

        List<User> users = userJpaRepository.findAll();

        for (User user : users) {
            try {
                dailyResultService.recordDailyResult(user.getId(), yesterday);
            } catch (Exception e) {
                log.error("일일 결과 기록 실패 userId={}", user.getId(), e);
            }
        }
        log.info("Daily Result Recording end");
    }
}
