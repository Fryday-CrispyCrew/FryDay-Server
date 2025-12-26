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
import java.util.List;

/**
 * 개별 투두 알림 스케줄러 (Ad-005)
 * - 매분 0초에 실행
 * - 발송 시간이 되었고 아직 발송 전인 알림 발송
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final TodoAlarmRepository todoAlarmRepository;
    private final PushService pushService;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    @Transactional
    public void sendDueNotifications() {
        LocalDateTime now = LocalDateTime.now();

        List<TodoAlarm> dueAlarms = todoAlarmRepository.findAllByStatusAndNotifyAtBefore(
                TodoAlarm.AlarmStatus.PENDING,
                now
        );

        if (dueAlarms.isEmpty()) {
            return;
        }

        log.info("Sending {} due notifications", dueAlarms.size());

        for (TodoAlarm alarm : dueAlarms) {
            try {
                pushService.sendToUser(alarm.getUser(), "FryDay 알림", alarm.getTodo().getDescription());
                alarm.markAsSent();
                log.info("Notification sent: todoId={}, userId={}", alarm.getTodo().getId(), alarm.getUser().getId());
            } catch (Exception e) {
                log.error("Failed to send notification for alarmId: {}", alarm.getId(), e);
            }
        }
    }
}
