package basakan.fryday.service.todo;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.todo.request.RecurrenceCreateRequest;
import basakan.fryday.controller.todo.request.RecurrenceOccurrenceCompletionRequest;
import basakan.fryday.controller.todo.request.RecurrenceUpdateRequest;
import basakan.fryday.controller.todo.response.TodoResponse;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.todo.Recurrence;
import basakan.fryday.domain.todo.RecurrenceException;
import basakan.fryday.domain.todo.RecurrenceOccurrenceState;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.todo.RecurrenceExceptionRepository;
import basakan.fryday.repository.todo.RecurrenceRepository;
import basakan.fryday.repository.todo.RecurrenceOccurrenceStateRepository;
import basakan.fryday.repository.todo.TodoRepository;
import basakan.fryday.service.todo.RecurrenceOccurrenceMaterializeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecurrenceService {

    private final RecurrenceRepository recurrenceRepository;
    private final TodoRepository todoRepository;
    private final RecurrenceOccurrenceStateRepository recurrenceOccurrenceStateRepository;
    private final RecurrenceExceptionRepository recurrenceExceptionRepository;
    private final CategoryRepository categoryRepository;
    private final RecurrenceOccurrenceMaterializeService materializeService;

    @Transactional
    public TodoResponse createRecurrence(Long userId, RecurrenceCreateRequest request) {
        Todo originalTodo = todoRepository.findById(request.getTodoId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        originalTodo.updateDate(request.getStartDate());

        Recurrence recurrence = Recurrence.builder()
                .userId(userId)
                .categoryId(originalTodo.getCategory().getId())
                .description(originalTodo.getDescription())
                .type(request.getType())
                .frequencyValues(request.getFrequencyValues() != null 
                        ? String.join(",", request.getFrequencyValues()) 
                        : null)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate()) // null이면 무한 반복
                .notificationTime(request.getNotificationTime())
                .lastGeneratedDate(request.getStartDate())
                .build();

        Recurrence savedRecurrence = recurrenceRepository.save(recurrence);

        // 원본 투두에 recurrenceId 연결
        originalTodo.setRecurrenceId(savedRecurrence.getId());

        // 반복 규칙만 저장하고, 투두는 대량 생성하지 않음
        // 가상 회차는 조회 시 동적으로 생성됨

        return TodoResponse.from(originalTodo);
    }

    // 반복 해제
    @Transactional
    public void deleteRecurrence(Long recurrenceId, Long userId) {
        Recurrence recurrence = recurrenceRepository.findById(recurrenceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        if (recurrence.getUserId() != userId) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        // 관련 예외 삭제
        List<RecurrenceException> exceptions = recurrenceExceptionRepository.findByRecurrenceId(recurrenceId);
        recurrenceExceptionRepository.deleteAll(exceptions);

        // 관련 상태 삭제
        List<RecurrenceOccurrenceState> states = recurrenceOccurrenceStateRepository.findByRecurrenceId(recurrenceId);
        recurrenceOccurrenceStateRepository.deleteAll(states);

        // 반복 규칙 삭제
        recurrenceRepository.delete(recurrence);
    }

    /**
     * 반복 투두의 가상 회차 완료 상태를 upsert하고 Todo 생성
     */
    @Transactional
    public TodoResponse toggleRecurrenceOccurrenceCompletion(Long recurrenceId, LocalDate occurrenceDate, Long userId) {
        Recurrence recurrence = recurrenceRepository.findById(recurrenceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        if (recurrence.getUserId() != userId) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        // 기존 상태 조회
        RecurrenceOccurrenceState existingState = recurrenceOccurrenceStateRepository
                .findByRecurrenceIdAndOccurrenceDate(recurrenceId, occurrenceDate)
                .orElse(null);

        RecurrenceOccurrenceState.Status newStatus;
        if (existingState != null) {
            // 기존 상태 토글
            newStatus = existingState.getStatus() == RecurrenceOccurrenceState.Status.COMPLETED
                    ? RecurrenceOccurrenceState.Status.IN_PROGRESS
                    : RecurrenceOccurrenceState.Status.COMPLETED;
            existingState.updateStatus(newStatus);
        } else {
            // 새 상태 생성 (기본값은 COMPLETED)
            newStatus = RecurrenceOccurrenceState.Status.COMPLETED;
            RecurrenceOccurrenceState newState = RecurrenceOccurrenceState.builder()
                    .recurrenceId(recurrenceId)
                    .occurrenceDate(occurrenceDate)
                    .status(newStatus)
                    .build();
            recurrenceOccurrenceStateRepository.save(newState);
        }

        // Todo 생성 또는 조회 (이미 존재하면 기존 Todo 반환)
        Todo todo = materializeService.materializeOccurrenceIfNotExists(userId, recurrenceId, occurrenceDate);

        // Todo 상태를 RecurrenceOccurrenceState와 동기화
        if (todo.getStatus() == Todo.Status.COMPLETED && newStatus == RecurrenceOccurrenceState.Status.IN_PROGRESS) {
            todo.toggleCompletion();
        } else if (todo.getStatus() == Todo.Status.IN_PROGRESS && newStatus == RecurrenceOccurrenceState.Status.COMPLETED) {
            todo.toggleCompletion();
        }

        return TodoResponse.from(todo);
    }

    // 반복 투두의 특정 회차를 분리(DETACHED)하고 단건 todo로 변환
    @Transactional
    public TodoResponse detachRecurrenceOccurrence(Long recurrenceId, LocalDate occurrenceDate, LocalDate newDate, Long userId) {
        Recurrence recurrence = recurrenceRepository.findById(recurrenceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        if (recurrence.getUserId() != userId) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        // DETACHED 예외가 이미 존재하는지 확인
        RecurrenceException existingException = recurrenceExceptionRepository
                .findByRecurrenceIdAndOccurrenceDate(recurrenceId, occurrenceDate)
                .orElse(null);

        if (existingException != null && existingException.getType() == RecurrenceException.ExceptionType.DETACHED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // DETACHED 예외 생성
        RecurrenceException exception = RecurrenceException.builder()
                .recurrenceId(recurrenceId)
                .occurrenceDate(occurrenceDate)
                .type(RecurrenceException.ExceptionType.DETACHED)
                .build();

        // 단건 todo 생성
        Category category = categoryRepository.findById(recurrence.getCategoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        Long maxOrder = todoRepository.findMaxDisplayOrder(userId, newDate);
        long displayOrder = (maxOrder == null) ? 1 : maxOrder + 1;

        // Todo.builder()는 recurrenceId를 필수로 받으므로, 0L을 전달하고 나중에 setRecurrenceId(null)로 변경
        Todo detachedTodo = Todo.builder()
                .description(recurrence.getDescription())
                .category(category)
                .date(newDate)
                .displayOrder(displayOrder)
                .recurrenceId(0L)  // 임시값, Builder 패턴 때문에 필요
                .build();
        detachedTodo.setRecurrenceId(null);  // 반복에서 분리되었으므로 null

        Todo savedTodo = todoRepository.save(detachedTodo);

        // 예외에 detached_todo_id 저장
        exception = RecurrenceException.builder()
                .recurrenceId(recurrenceId)
                .occurrenceDate(occurrenceDate)
                .type(RecurrenceException.ExceptionType.DETACHED)
                .detachedTodoId(savedTodo.getId())
                .build();

        recurrenceExceptionRepository.save(exception);

        return TodoResponse.from(savedTodo);
    }

    /**
     * 반복 투두의 특정 회차를 제외(CANCELLED)합니다.
     */
    @Transactional
    public void cancelRecurrenceOccurrence(Long recurrenceId, LocalDate occurrenceDate, Long userId) {
        Recurrence recurrence = recurrenceRepository.findById(recurrenceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        if (recurrence.getUserId() != userId) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        RecurrenceException existingException = recurrenceExceptionRepository
                .findByRecurrenceIdAndOccurrenceDate(recurrenceId, occurrenceDate)
                .orElse(null);

        if (existingException != null) {
            if (existingException.getType() == RecurrenceException.ExceptionType.DETACHED) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            return;
        }

        RecurrenceException exception = RecurrenceException.builder()
                .recurrenceId(recurrenceId)
                .occurrenceDate(occurrenceDate)
                .type(RecurrenceException.ExceptionType.CANCELLED)
                .build();

        recurrenceExceptionRepository.save(exception);
    }

    // 반복 규칙 수정
    @Transactional
    public void updateRecurrence(Long recurrenceId, RecurrenceUpdateRequest request, Long userId) {
        Recurrence recurrence = recurrenceRepository.findById(recurrenceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        if (recurrence.getUserId() != userId) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        String frequencyValuesStr = (request.getFrequencyValues() != null && !request.getFrequencyValues().isEmpty())
                ? String.join(",", request.getFrequencyValues())
                : null;

        recurrence.update(
                request.getType(),
                frequencyValuesStr,
                request.getStartDate(),
                request.getEndDate(),
                request.getNotificationTime()
        );
    }
}
