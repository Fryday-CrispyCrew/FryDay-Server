package basakan.fryday.scheduler;

import basakan.fryday.common.service.push.PushService;
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
 * 투두 추가 독려 알림 스케줄러 (Alarm-003)
 * - 매일 오전 09:00에 실행
 * - 당일 투두를 하나도 추가하지 않은 사용자에게 알림 발송
 * - 메시지: "튀김기에 새로운 튀김을 넣어주세요!"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EncourageAlarmScheduler {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final AgreementJpaRepository agreementJpaRepository;
    private final TodoRepository todoRepository;
    private final PushService pushService;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    @Transactional(readOnly = true)
    public void sendEncourageAlarm() {
        LocalDate today = LocalDate.now(KOREA_ZONE);
        log.info("Encourage Alarm start: {}", today);

        List<User> usersWithPushEnabled = agreementJpaRepository.findAllUsersWithPushNotificationEnabled();

        if (usersWithPushEnabled.isEmpty()) {
            log.info("No users with push notification enabled");
            return;
        }

        List<Long> userIdsWithTodos = todoRepository.findUserIdsWithTodosByDate(today);
        Set<Long> hasToDoUserIds = Set.copyOf(userIdsWithTodos);

        List<User> targetUsers = usersWithPushEnabled.stream()
                .filter(user -> !hasToDoUserIds.contains(user.getId()))
                .toList();

        if (targetUsers.isEmpty()) {
            log.info("All users already have todos for {}", today);
            return;
        }

        int notificationCount = 0;

        for (User user : targetUsers) {
            try {
                pushService.sendToUser(user, "FryDay", "튀김기에 새로운 튀김을 넣어주세요!");
                notificationCount++;
                log.info("Encourage alarm sent: userId={}", user.getId());
            } catch (Exception e) {
                log.error("Encourage alarm failed for userId={}", user.getId(), e);
            }
        }

        log.info("Encourage Alarm end: sent={}", notificationCount);
    }
}
