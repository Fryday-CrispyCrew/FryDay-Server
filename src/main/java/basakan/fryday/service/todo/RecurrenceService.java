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
import java.util.Set;
import java.util.stream.Collectors;

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

        Recurrence recurrence = Recurrence.builder()
                .userId(userId)
                .categoryId(originalTodo.getCategory().getId())
                .description(originalTodo.getDescription())
                .memo(originalTodo.getMemo())
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

        // Todo 의미 확인 필요
        RecurrenceException exception = RecurrenceException.builder()
                .recurrenceId(savedRecurrence.getId())
                .occurrenceDate(originalTodo.getDate())
                .type(RecurrenceException.ExceptionType.DELETED)
                .build();
        recurrenceExceptionRepository.save(exception);

        // 반복 규칙만 저장하고, 투두는 대량 생성하지 않음
        // 가상 회차는 조회 시 동적으로 생성됨

        return TodoResponse.from(originalTodo);
    }

    /**
     * 반복 해제: 해당 투두만 남기고, 원본 투두를 포함한 나머지 반복 투두를 삭제
     */
    @Transactional
    public void deleteRecurrence(Long todoId, Long userId) {
        Todo keepTodo = todoRepository.findById(todoId)
                .filter(t -> !t.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        if (!keepTodo.getCategory().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        Long recurrenceId = keepTodo.getRecurrenceId();
        if (recurrenceId == null) {
            throw new BusinessException(ErrorCode.NOT_RECURRING_TODO);
        }

        Recurrence recurrence = recurrenceRepository.findById(recurrenceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        if (recurrence.getUserId() != userId) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        List<Todo> todos = todoRepository.findAllByRecurrenceId(recurrenceId);
        for (Todo todo : todos) {
            if (todo.getId().equals(todoId)) {
                todo.setRecurrenceId(null);
            } else {
                todo.delete();
            }
        }

        List<RecurrenceException> exceptions = recurrenceExceptionRepository.findByRecurrenceId(recurrenceId);
        recurrenceExceptionRepository.deleteAll(exceptions);

        recurrenceRepository.delete(recurrence);
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

        List<RecurrenceException> detachedExceptions = recurrenceExceptionRepository.findByRecurrenceId(recurrenceId)
                .stream()
                .filter(e -> e.getType() == RecurrenceException.ExceptionType.DETACHED && e.getDetachedTodoId() != null)
                .toList();
        
        Set<Long> detachedTodoIds = detachedExceptions.stream()
                .map(RecurrenceException::getDetachedTodoId)
                .collect(Collectors.toSet());

        // 기존 반복 투두들 삭제 (분리된 투두 제외)
        List<Todo> todos = todoRepository.findAllByRecurrenceId(recurrenceId);
        for (Todo todo : todos) {
            if (!detachedTodoIds.contains(todo.getId())) {
                todo.delete();
            }
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

        // lastGeneratedDate를 새로운 startDate로 업데이트
        recurrence.updateLastGeneratedDate(request.getStartDate());

        return recurrence;
    }
}
