package basakan.fryday.service.todo;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.todo.Recurrence;
import basakan.fryday.domain.todo.RecurrenceException;
import basakan.fryday.domain.todo.RecurrenceOccurrenceState;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.todo.RecurrenceExceptionRepository;
import basakan.fryday.repository.todo.RecurrenceOccurrenceStateRepository;
import basakan.fryday.repository.todo.RecurrenceRepository;
import basakan.fryday.repository.todo.TodoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 반복 투두의 가상 회차를 실제 DB에 기록하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecurrenceOccurrenceMaterializeService {

    private final RecurrenceRepository recurrenceRepository;
    private final RecurrenceExceptionRepository recurrenceExceptionRepository;
    private final RecurrenceOccurrenceStateRepository recurrenceOccurrenceStateRepository;
    private final TodoRepository todoRepository;
    private final CategoryRepository categoryRepository;
    private final RecurrenceOccurrenceCalculator occurrenceCalculator;

    /**
     * 특정 사용자의 오늘 날짜 가상 회차를 실제 Todo로 생성
     */
    @Transactional
    public void materializeTodayOccurrences(Long userId, LocalDate today) {
        // 1. 해당 날짜에 발생하는 반복 규칙 조회
        List<Recurrence> recurrences = recurrenceRepository.findByUserIdAndDateRange(userId, today);

        if (recurrences.isEmpty()) {
            return;
        }

        List<Todo> todosToCreate = new ArrayList<>();

        for (Recurrence recurrence : recurrences) {
            try {
                // 2. 예외 조회 (CANCELLED와 DETACHED 모두 제외)
                List<RecurrenceException> exceptions = recurrenceExceptionRepository
                        .findByRecurrenceId(recurrence.getId());

                // 해당 날짜가 예외인지 확인
                boolean isException = exceptions.stream()
                        .anyMatch(e -> e.getOccurrenceDate().equals(today));

                if (isException) {
                    continue;
                }

                // 3. 발생일 계산 (해당 날짜만)
                Set<LocalDate> cancelledDates = exceptions.stream()
                        .map(RecurrenceException::getOccurrenceDate)
                        .collect(Collectors.toSet());

                List<LocalDate> occurrenceDates = occurrenceCalculator.calculateOccurrences(
                        recurrence, today, today, cancelledDates
                );

                // 해당 날짜에 발생하는 회차가 있는지 확인
                if (occurrenceDates.isEmpty() || !occurrenceDates.contains(today)) {
                    continue;
                }

                // 4. 이미 DB에 존재하는지 확인 (recurrenceId와 date로 조회)
                // 해당 날짜에 이미 같은 recurrenceId를 가진 Todo가 있는지 확인
                List<Todo> existingTodos = todoRepository.findAllByUserIdAndDate(userId, today);
                boolean alreadyExists = existingTodos.stream()
                        .anyMatch(todo -> todo.getRecurrenceId() != null 
                                && todo.getRecurrenceId().equals(recurrence.getId())
                                && todo.getDeletedAt() == null);

                if (alreadyExists) {
                    continue;
                }

                // 5. 상태 정보 조회
                RecurrenceOccurrenceState.Status status = RecurrenceOccurrenceState.Status.IN_PROGRESS;
                RecurrenceOccurrenceState existingState = recurrenceOccurrenceStateRepository
                        .findByRecurrenceIdAndOccurrenceDate(recurrence.getId(), today)
                        .orElse(null);

                if (existingState != null) {
                    status = existingState.getStatus();
                }

                // 6. Category 조회
                Category category = categoryRepository.findById(recurrence.getCategoryId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

                // 7. Todo 생성
                Long maxOrder = todoRepository.findMaxDisplayOrder(userId, today);
                long displayOrder = (maxOrder == null) ? 1 : maxOrder + 1;

                Todo todo = Todo.builder()
                        .description(recurrence.getDescription())
                        .category(category)
                        .date(today)
                        .displayOrder(displayOrder)
                        .recurrenceId(recurrence.getId())
                        .build();

                if (status == RecurrenceOccurrenceState.Status.COMPLETED) {
                    todo.toggleCompletion();
                }

                todosToCreate.add(todo);
            } catch (Exception e) {
                log.error("반복 투두 생성 실패 - recurrenceId: {}, date: {}", recurrence.getId(), today, e);
            }
        }

        if (!todosToCreate.isEmpty()) {
            todoRepository.saveAll(todosToCreate);
            log.info("반복 투두 생성 완료 - userId: {}, date: {}, count: {}", userId, today, todosToCreate.size());
        }
    }
}