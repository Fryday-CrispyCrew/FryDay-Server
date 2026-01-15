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

        todo.toggleCompletion();;

        return TodoResponse.from(todo);
    }

    @Transactional
    public MemoResponse updateMemo(Long todoId, Long userId, MemoRequest request) {
        Todo todo = todoRepository.findById(todoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        todo.updateMemo(request.getMemo());

        return MemoResponse.from(todo.getId(), todo.getMemo());
    }

    @Transactional
    public void deleteTodo(Long todoId, Long userId) {
        Todo todo = todoRepository.findById(todoId)
                .filter(t -> !t.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        todoRepository.delete(todo);
    }

    @Transactional
    public TodoResponse postponeToTomorrow(Long todoId, Long userId) {
        Todo todo = todoRepository.findById(todoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        if (!todo.getDate().equals(LocalDate.now())) {
            throw new BusinessException(ErrorCode.TODO_NOT_TODAY);
        }

        todo.updateDate(LocalDate.now().plusDays(1));

        return TodoResponse.from(todo);
    }

    @Transactional
    public TodoResponse moveToToday(Long todoId, Long userId) {
        Todo todo = todoRepository.findById(todoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        todo.updateDate(LocalDate.now());

        return TodoResponse.from(todo);
    }

    @Transactional
    public TodoResponse updateTodoDate(Long todoId, Long userId, TodoDateUpdateRequest request) {
        Todo todo = todoRepository.findById(todoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        if (request.getDate().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.PAST_DATE_NOT_ALLOWED);
        }

        todo.updateDate(request.getDate());

        return TodoResponse.from(todo);
    }

    @Transactional
    public TodoResponse updateCategory(Long todoId, Long userId, TodoCategoryUpdateRequest request) {
        Todo todo = todoRepository.findById(todoId)
                .filter(t -> !t.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        if (!todo.getCategory().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        Category newCategory = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        if (!newCategory.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        todo.updateCategory(newCategory);

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

    /**
     * 반복 투두 materialization을 별도 트랜잭션에서 수행
     */
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
                // REQUIRES_NEW로 분리된 트랜잭션에서 발생한 예외는 외부 트랜잭션에 영향을 주지 않음
                log.error("반복 투두 materialize 중 예상치 못한 예외 발생 - recurrenceId: {}, date: {}. 스킵하고 계속 진행", 
                        recurrence.getId(), date, e);
                continue;
            }
        }
    }

    /**
     * 투두 목록 조회를 별도 트랜잭션에서 수행
     * materialization이 완료된 후 조회하므로 새로 생성된 Todo를 포함하여 반환
     */
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

    /**
     * 투두 목록 조회
     * 1. 먼저 반복 투두를 materialize (별도 트랜잭션)
     * 2. 그 다음 투두 목록을 조회 (별도 읽기 전용 트랜잭션)
     * 이렇게 분리하여 materialization이 커밋된 후 조회하므로 새로 생성된 Todo를 포함하여 반환
     */
    public List<TodoListResponse> getTodoList(Long userId, LocalDate date, Long categoryId) {
        materializeRecurrenceOccurrences(userId, date, categoryId);
        return getTodoListInternal(userId, date, categoryId);
    }

    @Transactional(readOnly = true)
    public CharacterStatusResponse getDailyCharacterStatus(Long userId, LocalDate targetDate) {

        if (targetDate.isAfter(LocalDate.now())) {
            return CharacterStatusResponse.builder()
                    .status(CharacterStatus.CASE_FUTURE)
                    .imageCode(null) // 그래픽 생성 안 함
                    .description(CharacterStatus.CASE_FUTURE.getDescription())
                    .build();
        }

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
        Todo todo = todoRepository.findById(todoId)
                .filter(t -> !t.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        if (!todo.getCategory().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        todo.updateDescription(request.getDescription());

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
        if (status == CharacterStatus.CASE_D) {
            // Case E일 때만 e1, e2 중 랜덤 반환
            return Math.random() < 0.5 ? "e1_graphic" : "e2_graphic";
        }
        return status.getImageCode();
    }

}
