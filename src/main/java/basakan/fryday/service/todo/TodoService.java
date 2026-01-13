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
import basakan.fryday.domain.todo.RecurrenceException;
import basakan.fryday.domain.todo.RecurrenceOccurrenceState;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.todo.TodoAlarmRepository;
import basakan.fryday.repository.todo.TodoRepository;
import basakan.fryday.repository.todo.RecurrenceRepository;
import basakan.fryday.repository.todo.RecurrenceExceptionRepository;
import basakan.fryday.repository.todo.RecurrenceOccurrenceStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class TodoService {

    private final TodoRepository todoRepository;
    private final CategoryRepository categoryRepository;
    private final TodoAlarmRepository todoAlarmRepository;
    private final RecurrenceRepository recurrenceRepository;
    private final RecurrenceExceptionRepository recurrenceExceptionRepository;
    private final RecurrenceOccurrenceStateRepository recurrenceOccurrenceStateRepository;
    private final RecurrenceOccurrenceCalculator occurrenceCalculator;

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

    @Transactional(readOnly = true)
    public List<TodoListResponse> getTodoList(Long userId, LocalDate date, Long categoryId) {
        // 1. 일반 투두 목록 조회 (recurrenceId가 null인 투두 + DETACHED된 단건 투두)
        List<Todo> todos;
        if (categoryId == null) {
            todos = todoRepository.findAllByUserIdAndDate(userId, date);
        } else {
            todos = todoRepository.findAllByCategoryIdAndDate(categoryId, date);
        }

        // 2. 해당 날짜에 해당하는 반복 규칙 조회
        List<Recurrence> recurrences = recurrenceRepository.findByUserIdAndDateRange(userId, date);
        
        // 카테고리 필터링
        if (categoryId != null) {
            recurrences = recurrences.stream()
                    .filter(r -> r.getCategoryId() == categoryId)
                    .collect(Collectors.toList());
        }

        // 3. 반복 투두의 가상 회차 생성
        List<TodoListResponse> virtualOccurrences = new ArrayList<>();
        
        for (Recurrence recurrence : recurrences) {
            // 예외 조회 (CANCELLED와 DETACHED 모두 제외)
            List<RecurrenceException> exceptions = recurrenceExceptionRepository
                    .findByRecurrenceId(recurrence.getId());
            
            // 해당 날짜가 예외인지 확인
            boolean isException = exceptions.stream()
                    .anyMatch(e -> e.getOccurrenceDate().equals(date));
            
            if (isException) {
                continue;
            }

            // 발생일 계산 (해당 날짜만)
            Set<LocalDate> cancelledDates = exceptions.stream()
                    .map(RecurrenceException::getOccurrenceDate)
                    .collect(Collectors.toSet());

            List<LocalDate> occurrenceDates = occurrenceCalculator.calculateOccurrences(
                    recurrence, date, date, cancelledDates
            );

            // 해당 날짜에 발생하는 회차가 있는지 확인
            if (occurrenceDates.isEmpty() || !occurrenceDates.contains(date)) {
                continue;
            }

            // 상태 정보 조회
            Map<LocalDate, RecurrenceOccurrenceState.Status> statusMap = 
                    recurrenceOccurrenceStateRepository.findStatusMapByRecurrenceId(recurrence.getId());

            RecurrenceOccurrenceState.Status status = statusMap.getOrDefault(
                    date, RecurrenceOccurrenceState.Status.IN_PROGRESS
            );

            Long maxOrder = todoRepository.findMaxDisplayOrder(userId, date);
            long displayOrder = (maxOrder == null) ? 1 : maxOrder + 1;

            virtualOccurrences.add(TodoListResponse.fromVirtualOccurrence(
                    recurrence.getId(),
                    recurrence.getDescription(),
                    status.name(),
                    recurrence.getCategoryId(),
                    displayOrder,
                    date
            ));
        }

        // 4. 일반 투두와 가상 회차 합치기
        List<TodoListResponse> allResponses = Stream.concat(
                todos.stream()
                        .filter(todo -> todo.getRecurrenceId() == null)  // 일반 투두만 (기존 반복 투두 제외)
                        .map(TodoListResponse::from),
                virtualOccurrences.stream()
        ).collect(Collectors.toList());

        // displayOrder 기준으로 정렬
        allResponses.sort(Comparator.comparing(TodoListResponse::getDisplayOrder));

        return allResponses;
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
