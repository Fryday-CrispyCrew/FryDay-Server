package basakan.fryday.service.todo;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.todo.request.RecurrenceCreateRequest;
import basakan.fryday.controller.todo.request.RecurrenceUpdateRequest;
import basakan.fryday.controller.todo.response.TodoResponse;
import basakan.fryday.domain.todo.Recurrence;
import basakan.fryday.domain.todo.RecurrenceException;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.repository.todo.RecurrenceExceptionRepository;
import basakan.fryday.repository.todo.RecurrenceRepository;
import basakan.fryday.repository.todo.TodoRepository;
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
    private final RecurrenceExceptionRepository recurrenceExceptionRepository;

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

        // 반복으로 생성된 모든 Todo 삭제 (soft delete)
        List<Todo> todos = todoRepository.findAllByRecurrenceId(recurrenceId);
        for (Todo todo : todos) {
            todo.delete();
        }

        // 관련 예외 삭제
        List<RecurrenceException> exceptions = recurrenceExceptionRepository.findByRecurrenceId(recurrenceId);
        recurrenceExceptionRepository.deleteAll(exceptions);

        // 반복 규칙 삭제
        recurrenceRepository.delete(recurrence);
    }

    // 반복 투두의 특정 회차를 분리
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

        // 기존 Todo 조회
        List<Todo> existingTodos = todoRepository.findAllByUserIdAndDate(userId, occurrenceDate);
        Todo existingTodo = existingTodos.stream()
                .filter(todo -> todo.getRecurrenceId() != null
                        && todo.getRecurrenceId().equals(recurrenceId)
                        && todo.getDeletedAt() == null)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        // 날짜 변경
        if (!occurrenceDate.equals(newDate)) {
            existingTodo.updateDate(newDate);
            // displayOrder 재계산 (새 날짜 기준)
            Long maxOrder = todoRepository.findMaxDisplayOrder(userId, newDate);
            long displayOrder = (maxOrder == null) ? 1 : maxOrder + 1;
            existingTodo.updateDisplayOrder(displayOrder);
        }

        // recurrenceId를 null로 변경 (반복에서 분리)
        existingTodo.setRecurrenceId(null);

        // DETACHED 예외 생성
        RecurrenceException exception = RecurrenceException.builder()
                .recurrenceId(recurrenceId)
                .occurrenceDate(occurrenceDate)
                .type(RecurrenceException.ExceptionType.DETACHED)
                .detachedTodoId(existingTodo.getId())
                .build();

        recurrenceExceptionRepository.save(exception);

        return TodoResponse.from(existingTodo);
    }

    /**
     * 반복 투두의 특정 회차를 제외(CANCELLED)합니다.
     */
    // 현재 기획상 미존재
//    @Transactional
//    public void cancelRecurrenceOccurrence(Long recurrenceId, LocalDate occurrenceDate, Long userId) {
//        Recurrence recurrence = recurrenceRepository.findById(recurrenceId)
//                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));
//
//        if (recurrence.getUserId() != userId) {
//            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
//        }
//
//        RecurrenceException existingException = recurrenceExceptionRepository
//                .findByRecurrenceIdAndOccurrenceDate(recurrenceId, occurrenceDate)
//                .orElse(null);
//
//        if (existingException != null) {
//            if (existingException.getType() == RecurrenceException.ExceptionType.DETACHED) {
//                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
//            }
//            return;
//        }
//
//        RecurrenceException exception = RecurrenceException.builder()
//                .recurrenceId(recurrenceId)
//                .occurrenceDate(occurrenceDate)
//                .type(RecurrenceException.ExceptionType.CANCELLED)
//                .build();
//
//        recurrenceExceptionRepository.save(exception);
//    }

    // 반복 규칙 수정
    @Transactional
    public Recurrence updateRecurrence(Long recurrenceId, RecurrenceUpdateRequest request, Long userId) {
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

        return recurrence;
    }
}
