package basakan.fryday.scheduler;

import basakan.fryday.common.service.push.PushService;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.auth.UserJpaRepository;
import basakan.fryday.repository.todo.TodoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 실패 확정 알림 스케줄러 (Alarm-002)
 * - 매일 새벽 00:05에 실행
 * - 전날 미완료 투두가 있는 사용자에게 알림 발송
 * - 메시지: "튀김이 타버렸어요. 새로운 계획을 세워요!"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BurntAlarmScheduler {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final UserJpaRepository userJpaRepository;
    private final TodoRepository todoRepository;
    private final PushService pushService;

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    @Transactional(readOnly = true)
    public void sendBurntAlarm() {
        LocalDate yesterday = LocalDate.now(KOREA_ZONE).minusDays(1);
        log.info("Burnt Alarm start: {}", yesterday);

        List<Long> userIdsWithBurntTodos = todoRepository.findUserIdsWithTodosByDateAndStatus(yesterday, Todo.Status.IN_PROGRESS);

        if (userIdsWithBurntTodos.isEmpty()) {
            log.info("No burnt todos found for {}", yesterday);
            return;
        }

        List<User> targetUsers = userJpaRepository.findAllById(userIdsWithBurntTodos);

        int notificationCount = 0;

        for (User user : targetUsers) {
            try {
                pushService.sendToUser(user, "튀김이 타버렸어요...", "새로운 계획을 세워요!");
                notificationCount++;
                log.info("Burnt alarm sent: userId={}", user.getId());
            } catch (Exception e) {
                log.error("Burnt alarm failed for userId={}", user.getId(), e);
            }
        }

        log.info("Burnt Alarm end: sent={}", notificationCount);
    }
}
