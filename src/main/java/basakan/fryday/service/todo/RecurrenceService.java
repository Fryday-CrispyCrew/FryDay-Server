package basakan.fryday.service.todo;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.todo.request.RecurrenceCreateRequest;
import basakan.fryday.controller.todo.request.RecurrenceUpdateRequest;
import basakan.fryday.controller.todo.response.TodoResponse;
import basakan.fryday.domain.todo.EndType;
import basakan.fryday.domain.todo.Recurrence;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.repository.todo.RecurrenceRepository;
import basakan.fryday.repository.todo.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecurrenceService {

    private final RecurrenceRepository recurrenceRepository;
    private final TodoRepository todoRepository;

    @Transactional
    public TodoResponse createRecurrence(Long userId, RecurrenceCreateRequest request) {
        Todo originalTodo = todoRepository.findById(request.getTodoId())
                .filter(t -> !t.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        if (!originalTodo.getCategory().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        EndType endType = (request.getEndDate() != null) ? EndType.UNTIL : EndType.NONE;

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
                .endDate(request.getEndDate())
                .endType(endType)
                .notificationTime(request.getNotificationTime())
                .lastGeneratedDate(request.getStartDate())
                .build();

        Recurrence savedRecurrence = recurrenceRepository.save(recurrence);

        int updated = todoRepository.updateRecurrenceIdByIdAndUserId(
                request.getTodoId(), userId, savedRecurrence.getId());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        Todo todo = todoRepository.findById(request.getTodoId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));
        return TodoResponse.from(todo);
    }

    /**
     * 반복 해제: 해당 투두만 남기고 나머지 반복 인스턴스 삭제
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

        recurrenceRepository.delete(recurrence);
    }

    @Transactional
    public void deleteAllRecurringTodos(Long recurrenceId, Long userId) {
        Recurrence recurrence = recurrenceRepository.findById(recurrenceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        if (recurrence.getUserId() != userId) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        List<Todo> todos = todoRepository.findAllByRecurrenceId(recurrenceId);
        for (Todo todo : todos) {
            todo.delete();
        }

        recurrenceRepository.delete(recurrence);
    }

    @Transactional
    public Recurrence updateRecurrence(Long recurrenceId, RecurrenceUpdateRequest request, Long userId) {
        Recurrence recurrence = recurrenceRepository.findById(recurrenceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        if (recurrence.getUserId() != userId) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        // 기존 반복 인스턴스 삭제
        List<Todo> todos = todoRepository.findAllByRecurrenceId(recurrenceId);
        for (Todo todo : todos) {
            todo.delete();
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

        recurrence.updateLastGeneratedDate(request.getStartDate());

        return recurrence;
    }
}
