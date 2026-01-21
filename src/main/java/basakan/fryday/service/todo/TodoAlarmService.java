package basakan.fryday.service.todo;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.domain.todo.TodoAlarm;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.todo.TodoAlarmRepository;
import basakan.fryday.repository.todo.TodoRepository;
import basakan.fryday.service.user.UserReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional
public class TodoAlarmService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final TodoAlarmRepository todoAlarmRepository;
    private final TodoRepository todoRepository;
    private final UserReadService userReadService;

    public void setTodoAlarm(Long todoId, Long userId, LocalDateTime notifyAt) {
        LocalDateTime now = LocalDateTime.now(KOREA_ZONE);
        if (notifyAt.isBefore(now)) {
            throw new BusinessException(ErrorCode.ALARM_TIME_IN_PAST);
        }

        Todo todo = todoRepository.findById(todoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        if (!todo.getCategory().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        User user = userReadService.findById(userId);

        todoAlarmRepository.findByTodoId(todoId)
                .ifPresentOrElse(
                        existingAlarm -> existingAlarm.changeTime(notifyAt),
                        () -> {
                            TodoAlarm newAlarm = TodoAlarm.create(todo, user, notifyAt);
                            todoAlarmRepository.save(newAlarm);
                        }
                );
    }

    public void deleteTodoAlarm(Long todoId, Long userId) {
        Todo todo = todoRepository.findById(todoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        if (!todo.getCategory().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        todoAlarmRepository.deleteByTodoId(todoId);
    }
}
