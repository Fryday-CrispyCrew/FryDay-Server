package basakan.fryday.service.todo;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.todo.Recurrence;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.domain.todo.TodoAlarm;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.todo.RecurrenceRepository;
import basakan.fryday.repository.todo.TodoAlarmRepository;
import basakan.fryday.repository.todo.TodoRepository;
import basakan.fryday.service.user.UserReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 반복 회차 1건을 실제 Todo로 기록하는 쓰기 담당.
 * <p>
 * 회차 1건의 실패가 나머지 회차로 번지지 않아야 하므로 {@code REQUIRES_NEW}로 트랜잭션을 분리한다.
 * 이 격리는 프록시를 거칠 때만 성립하기 때문에, 반복문을 도는 오케스트레이션
 * ({@link RecurrenceOccurrenceMaterializeService})과 반드시 다른 빈으로 둔다.
 */
@Service
@RequiredArgsConstructor
public class RecurrenceOccurrenceWriter {

    private final RecurrenceRepository recurrenceRepository;
    private final TodoRepository todoRepository;
    private final CategoryRepository categoryRepository;
    private final TodoAlarmRepository todoAlarmRepository;
    private final RecurrenceOccurrenceCalculator occurrenceCalculator;
    private final UserReadService userReadService;

    /**
     * 특정 반복 규칙의 특정 날짜 회차를 생성한다. 이미 있으면 만들지 않는다.
     *
     * @return 살아있는 회차. 이미 삭제된 회차면 {@code null}(재생성하지 않는다)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Todo materializeIfAbsent(Long userId, Long recurrenceId, LocalDate occurrenceDate) {
        Recurrence recurrence = recurrenceRepository.findById(recurrenceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        if (recurrence.getUserId() != userId) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        // 행이 있으면 이미 처리된 날짜다. 삭제된 회차는 되살리지 않으므로 null을 돌려준다.
        // 존재 확인과 반환값 조회를 한 번에 끝내기 위해 exists 대신 조회 한 번으로 판단한다.
        Optional<Todo> existing = todoRepository.findByRecurrenceIdAndDate(recurrenceId, occurrenceDate);
        if (existing.isPresent()) {
            return existing.filter(t -> !t.isDeleted()).orElse(null);
        }

        // 실제로 발생하는 날짜인지 확인
        List<LocalDate> occurrenceDates = occurrenceCalculator.calculateOccurrences(
                recurrence, occurrenceDate, occurrenceDate
        );
        if (occurrenceDates.isEmpty() || !occurrenceDates.contains(occurrenceDate)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Category category = categoryRepository.findById(recurrence.getCategoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        Long maxOrder = todoRepository.findMaxDisplayOrder(userId, occurrenceDate);
        long displayOrder = (maxOrder == null) ? 1 : maxOrder + 1;

        Todo todo = Todo.builder()
                .description(recurrence.getDescription())
                .category(category)
                .date(occurrenceDate)
                .displayOrder(displayOrder)
                .recurrenceId(recurrence.getId())
                .memo(recurrence.getMemo())
                .build();

        Todo savedTodo = todoRepository.save(todo);

        if (recurrence.isAlarmEnabled()) {
            attachAlarm(userId, savedTodo, occurrenceDate, recurrence);
        }

        return savedTodo;
    }

    private void attachAlarm(Long userId, Todo todo, LocalDate occurrenceDate, Recurrence recurrence) {
        LocalDateTime notifyAt = LocalDateTime.of(occurrenceDate, recurrence.getNotificationTime());

        todoAlarmRepository.findByTodoId(todo.getId())
                .ifPresentOrElse(
                        existingAlarm -> existingAlarm.changeTime(notifyAt),
                        () -> {
                            User user = userReadService.findById(userId);
                            todoAlarmRepository.save(TodoAlarm.create(todo, user, notifyAt));
                        }
                );
    }
}
