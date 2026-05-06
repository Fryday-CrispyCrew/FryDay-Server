package basakan.fryday.service.todo;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.todo.request.*;
import basakan.fryday.controller.dto.OrderUpdateRequest;
import basakan.fryday.controller.todo.response.CharacterStatusResponse;
import basakan.fryday.controller.todo.response.MemoResponse;
import basakan.fryday.controller.todo.response.TodoListResponse;
import basakan.fryday.controller.todo.response.TodoResponse;
import basakan.fryday.domain.BaseEntity;
import basakan.fryday.controller.todo.response.TodoDetailResponse;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.todo.CharacterStatus;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.domain.todo.TodoAlarm;
import basakan.fryday.domain.todo.Recurrence;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.todo.TodoAlarmRepository;
import basakan.fryday.repository.todo.TodoRepository;
import basakan.fryday.repository.todo.RecurrenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TodoService {

    private final TodoRepository todoRepository;
    private final CategoryRepository categoryRepository;
    private final TodoAlarmRepository todoAlarmRepository;
    private final RecurrenceRepository recurrenceRepository;
    private final RecurrenceOccurrenceMaterializeService materializeService;

    @Transactional
    public TodoResponse saveTodo(TodoSaveRequest request, Long userId) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        if (!category.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        Todo todo = request.toEntity(category);

        Long maxOrder = todoRepository.findMaxDisplayOrder(category.getUserId(), todo.getDate());
        long nextOrder = (maxOrder == null) ? 1 : maxOrder + 1;

        todo.updateDisplayOrder(nextOrder);

        Todo savedTodo = todoRepository.save(todo);

        return TodoResponse.from(savedTodo);
    }

    @Transactional
    public TodoResponse toggleTodoCompletion(Long todoId, Long userId) {
        Todo todo = todoRepository.findById(todoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        todo.toggleCompletion();

        return TodoResponse.from(todo);
    }

    @Transactional
    public MemoResponse updateMemo(Long todoId, Long userId, MemoRequest request) {
        int updated = todoRepository.updateMemoByIdAndUserId(todoId, userId, request.getMemo());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }
        Todo todo = todoRepository.findById(todoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));
        return MemoResponse.from(todo.getId(), todo.getMemo());
    }

    @Transactional
    public void deleteTodo(Long todoId, Long userId) {
        Todo todo = todoRepository.findById(todoId)
                .filter(t -> !t.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        if (!todo.getCategory().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        todoAlarmRepository.deleteByTodoId(todoId);
        todo.delete();
    }

    @Transactional
    public TodoResponse postponeToTomorrow(Long todoId, Long userId) {
        Todo todo = todoRepository.findById(todoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        if (!todo.getCategory().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        if (!todo.getDate().equals(LocalDate.now())) {
            throw new BusinessException(ErrorCode.TODO_NOT_TODAY);
        }

        return moveOrCopyTodoToDate(todo, LocalDate.now().plusDays(1), userId);
    }

    @Transactional
    public TodoResponse moveToToday(Long todoId, Long userId) {
        Todo todo = todoRepository.findById(todoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        if (!todo.getCategory().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        return moveOrCopyTodoToDate(todo, LocalDate.now(), userId);
    }

    private TodoResponse moveOrCopyTodoToDate(Todo todo, LocalDate targetDate, Long userId) {
        if (todo.isCompleted()) {
            Todo copiedTodo = copyTodoToDate(todo, targetDate);
            return TodoResponse.from(copiedTodo);
        }

        // 반복 투두는 날짜 이동 시 recurrence에서 분리(detach)
        if (todo.getRecurrenceId() != null) {
            todo.setRecurrenceId(null);
        }

        todo.updateDate(targetDate);
        return TodoResponse.from(todo);
    }

    private Todo copyTodoToDate(Todo source, LocalDate targetDate) {
        Long userId = source.getCategory().getUserId();
        Long maxOrder = todoRepository.findMaxDisplayOrder(userId, targetDate);
        long nextOrder = (maxOrder == null) ? 1 : maxOrder + 1;

        Todo copiedTodo = Todo.builder()
                .description(source.getDescription())
                .category(source.getCategory())
                .date(targetDate)
                .displayOrder(nextOrder)
                .recurrenceId(null)
                .memo(source.getMemo())
                .build();

        return todoRepository.save(copiedTodo);
    }

    @Transactional
    public TodoResponse updateTodoDate(Long todoId, Long userId, TodoDateUpdateRequest request) {
        if (request.getDate().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.PAST_DATE_NOT_ALLOWED);
        }

        // 일반 투두(반복 아님): date 필드만 갱신
        int updated = todoRepository.updateDateByIdAndUserIdForNonRecurring(todoId, userId, request.getDate());
        if (updated > 0) {
            Todo todo = todoRepository.findById(todoId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));
            return TodoResponse.from(todo);
        }

        // 반복 투두: recurrence에서 분리 후 날짜 이동
        Todo todo = todoRepository.findById(todoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        if (!todo.getCategory().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        if (todo.getRecurrenceId() == null) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        LocalDate newDate = request.getDate();
        todo.updateDate(newDate);
        Long maxOrder = todoRepository.findMaxDisplayOrder(userId, newDate);
        long displayOrder = (maxOrder == null) ? 1 : maxOrder + 1;
        todo.updateDisplayOrder(displayOrder);

        // recurrence에서 분리 (독립 투두로 변환)
        todo.setRecurrenceId(null);

        return TodoResponse.from(todo);
    }

    @Transactional
    public TodoResponse updateCategory(Long todoId, Long userId, TodoCategoryUpdateRequest request) {
        Category newCategory = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        if (!newCategory.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        int updated = todoRepository.updateCategoryByIdAndUserId(todoId, userId, request.getCategoryId());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }
        Todo todo = todoRepository.findById(todoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));
        return TodoResponse.from(todo);
    }

    @Transactional
    public void reorderTodos(Long userId, LocalDate date, OrderUpdateRequest request) {
        List<Long> idList = request.getIds();

        List<Todo> todos = todoRepository.findAllByCategory_UserIdAndDateAndDeletedAtIsNullOrderByDisplayOrderAsc(userId, date);

        Map<Long, Todo> todoMap = todos.stream()
                .collect(Collectors.toMap(BaseEntity::getId, t -> t));

        for (int i = 0; i < idList.size(); i++) {
            Todo todo = todoMap.get(idList.get(i));
            if (todo != null) {
                todo.updateDisplayOrder((long) (i + 1));
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void materializeRecurrenceOccurrences(Long userId, LocalDate date, Long categoryId) {
        List<Recurrence> recurrences = recurrenceRepository.findByUserIdAndDateRange(userId, date);

        if (categoryId != null) {
            recurrences = recurrences.stream()
                    .filter(r -> r.getCategoryId() == categoryId)
                    .collect(Collectors.toList());
        }

        for (Recurrence recurrence : recurrences) {
            try {
                materializeService.materializeOccurrenceIfNotExists(userId, recurrence.getId(), date);
            } catch (BusinessException e) {
                if (e.getErrorCode() == ErrorCode.INVALID_INPUT_VALUE) {
                    continue;
                }
                throw e;
            } catch (Exception e) {
                log.error("반복 투두 materialize 중 예상치 못한 예외 발생 - recurrenceId: {}, date: {}. 스킵하고 계속 진행",
                        recurrence.getId(), date, e);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<TodoListResponse> getTodoListInternal(Long userId, LocalDate date, Long categoryId) {
        List<Todo> todos;
        if (categoryId == null) {
            todos = todoRepository.findAllByUserIdAndDate(userId, date);
        } else {
            todos = todoRepository.findAllByCategoryIdAndDate(categoryId, date);
        }

        List<TodoListResponse> allResponses = todos.stream()
                .map(TodoListResponse::from)
                .collect(Collectors.toList());

        allResponses.sort(Comparator.comparing(TodoListResponse::getDisplayOrder));

        return allResponses;
    }

    public List<TodoListResponse> getTodoList(Long userId, LocalDate date, Long categoryId) {
        materializeRecurrenceOccurrences(userId, date, categoryId);
        return getTodoListInternal(userId, date, categoryId);
    }

    @Transactional(readOnly = true)
    public CharacterStatusResponse getDailyCharacterStatus(Long userId, LocalDate targetDate) {
        List<Todo> todos = todoRepository.findAllByUserIdAndDate(userId, targetDate);

        int totalCount = todos.size();
        int completedCount = (int) todos.stream().filter(Todo::isCompleted).count();
        LocalDateTime now = LocalDateTime.now();

        CharacterStatus status = CharacterStatus.determine(totalCount, completedCount, targetDate, now);

        String imageCode = resolveImageCode(status);

        return CharacterStatusResponse.builder()
                .status(status)
                .imageCode(imageCode)
                .description(status.getDescription())
                .build();
    }

    @Transactional
    public TodoResponse updateDescription(Long todoId, Long userId, TodoDescriptionUpdateRequest request) {
        int updated = todoRepository.updateDescriptionByIdAndUserId(todoId, userId, request.getDescription());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }
        Todo todo = todoRepository.findById(todoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));
        return TodoResponse.from(todo);
    }

    @Transactional(readOnly = true)
    public TodoDetailResponse getTodoDetail(Long todoId, Long userId) {
        Todo todo = todoRepository.findById(todoId)
                .filter(t -> !t.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        if (!todo.getCategory().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        TodoAlarm todoAlarm = todoAlarmRepository.findByTodoId(todoId).orElse(null);

        Recurrence recurrence = null;
        if (todo.getRecurrenceId() != null) {
            recurrence = recurrenceRepository.findById(todo.getRecurrenceId()).orElse(null);
        }

        return TodoDetailResponse.from(todo, todoAlarm, recurrence);
    }

    private String resolveImageCode(CharacterStatus status) {
        return status.getImageCode();
    }
}
