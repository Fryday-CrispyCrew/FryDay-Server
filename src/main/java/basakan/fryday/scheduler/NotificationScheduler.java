package basakan.fryday.scheduler;

import basakan.fryday.common.service.push.PushService;
import basakan.fryday.domain.todo.TodoAlarm;
import basakan.fryday.repository.todo.TodoAlarmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 개별 투두 알림 스케줄러 (Ad-005)
 * - 매분 0초에 실행
 * - 발송 시간이 되었고 아직 발송 전인 알림 발송
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private static final List<String[]> MESSAGE_TEMPLATES = List.of(
            new String[]{"지금 안 하면 튀김이 타요!", "%s 할 시간이에요!"},
            new String[]{"튀김이 가장 맛있을 시간이에요!", "%s 튀겨볼까요?"},
            new String[]{"%s 튀김이 노릇해지는 중...", "지금 꺼낼 타이밍이에요!"}
    );

    private final TodoAlarmRepository todoAlarmRepository;
    private final PushService pushService;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    @Transactional
    public void sendDueNotifications() {
        LocalDateTime now = LocalDateTime.now(KOREA_ZONE);

        List<TodoAlarm> dueAlarms = todoAlarmRepository.findAllByStatusAndNotifyAtBeforeOrEqual(
                TodoAlarm.AlarmStatus.PENDING,
                now
        );

        if (dueAlarms.isEmpty()) {
            return;
        }

        log.info("Sending {} due notifications", dueAlarms.size());

        for (TodoAlarm alarm : dueAlarms) {
            try {
                if (alarm.getTodo() == null || alarm.getTodo().isDeleted()) {
                    log.warn("Todo is deleted, skipping alarm: alarmId={}", alarm.getId());
                    alarm.markAsSent();
                    continue;
                }

                String todoName = alarm.getTodo().getDescription();
                String[] template = pickRandomTemplate();
                String title = String.format(template[0], todoName);
                String message = String.format(template[1], todoName);
                pushService.sendToUser(alarm.getUser(), title, message);
                alarm.markAsSent();
                log.info("Notification sent: todoId={}, userId={}", alarm.getTodo().getId(), alarm.getUser().getId());
            } catch (Exception e) {
                boolean maxRetryExceeded = alarm.incrementFailCount();
                if (maxRetryExceeded) {
                    log.error("Notification failed permanently after max retries: alarmId={}, todoId={}",
                            alarm.getId(), alarm.getTodo().getId(), e);
                } else {
                    log.warn("Notification failed, will retry: alarmId={}, failCount={}",
                            alarm.getId(), alarm.getFailCount(), e);
                }
            }
        }
    }

    private String[] pickRandomTemplate() {
        return MESSAGE_TEMPLATES.get(ThreadLocalRandom.current().nextInt(MESSAGE_TEMPLATES.size()));
    }
}
