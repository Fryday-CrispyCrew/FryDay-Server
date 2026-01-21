package basakan.fryday.scheduler;

import basakan.fryday.common.service.push.PushService;
import basakan.fryday.domain.todo.Todo.Status;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.auth.AgreementJpaRepository;
import basakan.fryday.repository.todo.TodoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * 마감 임박 알림 스케줄러 (Alarm-001)
 * - 매일 오후 10시(22:00)에 실행
 * - 당일 미완료 투두가 1개 이상 존재하는 사용자에게 알림 발송
 * - 메시지: "튀김이 타기전에 완료하세요!"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadlineAlarmScheduler {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final AgreementJpaRepository agreementJpaRepository;
    private final TodoRepository todoRepository;
    private final PushService pushService;

    @Scheduled(cron = "0 0 22 * * *", zone = "Asia/Seoul")
    @Transactional(readOnly = true)
    public void sendDeadlineAlarm() {
        LocalDate today = LocalDate.now(KOREA_ZONE);
        log.info("Deadline Alarm start: {}", today);

        List<User> usersWithPushEnabled = agreementJpaRepository.findAllUsersWithPushNotificationEnabled();

        if (usersWithPushEnabled.isEmpty()) {
            log.info("No users with push notification enabled");
            return;
        }

        List<Long> userIdsWithIncompleteTodos = todoRepository.findUserIdsWithTodosByDateAndStatus(
                today, Status.IN_PROGRESS
        );

        if (userIdsWithIncompleteTodos.isEmpty()) {
            log.info("No users with incomplete todos for {}", today);
            return;
        }

        Set<Long> incompleteUserIds = Set.copyOf(userIdsWithIncompleteTodos);
        List<User> targetUsers = usersWithPushEnabled.stream()
                .filter(user -> incompleteUserIds.contains(user.getId()))
                .toList();

        int notificationCount = 0;

        for (User user : targetUsers) {
            try {
                pushService.sendToUser(user, "FryDay", "튀김이 타기전에 완료하세요!");
                notificationCount++;
                log.info("Deadline alarm sent: userId={}", user.getId());
            } catch (Exception e) {
                log.error("Deadline alarm failed for userId={}", user.getId(), e);
            }
        }

        log.info("Deadline Alarm end: sent={}", notificationCount);
    }
}
