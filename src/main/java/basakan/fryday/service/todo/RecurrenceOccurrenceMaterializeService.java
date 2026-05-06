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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecurrenceOccurrenceMaterializeService {

    private final RecurrenceRepository recurrenceRepository;
    private final TodoRepository todoRepository;
    private final CategoryRepository categoryRepository;
    private final RecurrenceOccurrenceCalculator occurrenceCalculator;
    private final TodoAlarmRepository todoAlarmRepository;
    private final UserReadService userReadService;

    /**
     * 특정 반복 투두의 특정 날짜 인스턴스를 생성 (이미 존재하면 생성하지 않음)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Todo materializeOccurrenceIfNotExists(Long userId, Long recurrenceId, LocalDate occurrenceDate) {
        Recurrence recurrence = recurrenceRepository.findById(recurrenceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        if (recurrence.getUserId() != userId) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        // 이미 존재하는지 확인 (삭제된 인스턴스도 포함 — 재생성 방지)
        boolean alreadyExists = todoRepository.existsByRecurrenceIdAndDate(recurrenceId, occurrenceDate);
        if (alreadyExists) {
            // 살아있는 인스턴스만 반환, 삭제된 경우 null (재생성 방지이지만 반환값은 null)
            return todoRepository.findByRecurrenceIdAndDate(recurrenceId, occurrenceDate)
                    .filter(t -> !t.isDeleted())
                    .orElse(null);
        }

        // 발생일 계산으로 실제로 발생하는 날짜인지 확인
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

        if (recurrence.isAlarmEnabled() && recurrence.getNotificationTime() != null) {
            todoAlarmRepository.findByTodoId(savedTodo.getId())
                    .ifPresentOrElse(
                            existingAlarm -> {
                                LocalDateTime notifyAt = LocalDateTime.of(occurrenceDate, recurrence.getNotificationTime());
                                existingAlarm.changeTime(notifyAt);
                            },
                            () -> {
                                User user = userReadService.findById(userId);
                                LocalDateTime notifyAt = LocalDateTime.of(occurrenceDate, recurrence.getNotificationTime());
                                TodoAlarm todoAlarm = TodoAlarm.create(savedTodo, user, notifyAt);
                                todoAlarmRepository.save(todoAlarm);
                            }
                    );
        }

        return savedTodo;
    }

    /**
     * 특정 사용자의 오늘 날짜 인스턴스를 생성
     */
    @Transactional
    public void materializeTodayOccurrences(Long userId, LocalDate today) {
        List<Recurrence> recurrences = recurrenceRepository.findByUserIdAndDateRange(userId, today);

        if (recurrences.isEmpty()) {
            return;
        }

        int createdCount = 0;

        for (Recurrence recurrence : recurrences) {
            try {
                List<LocalDate> occurrenceDates = occurrenceCalculator.calculateOccurrences(
                        recurrence, today, today
                );

                if (occurrenceDates.isEmpty() || !occurrenceDates.contains(today)) {
                    continue;
                }

                // 이미 존재하면 스킵 (삭제된 인스턴스도 포함)
                if (todoRepository.existsByRecurrenceIdAndDate(recurrence.getId(), today)) {
                    continue;
                }

                try {
                    materializeOccurrenceIfNotExists(userId, recurrence.getId(), today);
                    createdCount++;
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.INVALID_INPUT_VALUE) {
                        continue;
                    }
                    throw e;
                }
            } catch (Exception e) {
                log.error("반복 투두 생성 실패 - recurrenceId: {}, date: {}", recurrence.getId(), today, e);
            }
        }

        if (createdCount > 0) {
            log.info("반복 투두 생성 완료 - userId: {}, date: {}, count: {}", userId, today, createdCount);
        }
    }
}
