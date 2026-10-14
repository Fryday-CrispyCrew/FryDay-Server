package basakan.fryday.service.todo;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.domain.todo.TodoAlarm;
import basakan.fryday.repository.todo.TodoAlarmRepository;
import basakan.fryday.service.user.UserReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * 회차의 유효 알림 시각에 맞춰 발송 행(todo_alarms)을 맞추는 단일 지점.
 * <p>
 * 유효 시각이 없거나 이미 지났으면 대기 중(PENDING) 행을 지워 잘못된 시각의 발송을 막는다.
 * SENT/FAILED 행은 발송 이력이므로 어떤 경우에도 건드리지 않는다. 미래 시각이면 upsert한다.
 */
@Service
@RequiredArgsConstructor
public class TodoAlarmSynchronizer {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final TodoAlarmRepository todoAlarmRepository;
    private final UserReadService userReadService;

    public void sync(Todo instance, Long userId, LocalTime effectiveTime) {
        if (!instance.getCategory().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        if (effectiveTime == null) {
            todoAlarmRepository.deletePendingByTodoId(instance.getId());
            return;
        }

        LocalDateTime notifyAt = LocalDateTime.of(instance.getDate(), effectiveTime);
        if (notifyAt.isBefore(LocalDateTime.now(KOREA_ZONE))) {
            todoAlarmRepository.deletePendingByTodoId(instance.getId());
            return;
        }

        todoAlarmRepository.findByTodoId(instance.getId())
                .ifPresentOrElse(
                        alarm -> alarm.changeTime(notifyAt),
                        () -> todoAlarmRepository.save(
                                TodoAlarm.create(instance, userReadService.findById(userId), notifyAt))
                );
    }

}
