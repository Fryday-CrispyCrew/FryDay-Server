package basakan.fryday.service;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.dto.*;
import basakan.fryday.domain.BaseEntity;
import basakan.fryday.domain.Category;
import basakan.fryday.domain.Todo;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TodoService {

    private final TodoRepository todoRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public TodoResponse saveTodo(TodoSaveRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        Todo todo = request.toEntity(category);

        Long maxOrder = todoRepository.findMaxDisplayOrder(category.getUserId(), todo.getDate());
        long nextOrder = (maxOrder == null) ? 1 : maxOrder + 1;

        todo.updateDisplayOrder(nextOrder);

        Todo saveTodo = todoRepository.save(todo);

        return TodoResponse.from(saveTodo);
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

        if (todo.isFailed()) {
            throw new BusinessException(ErrorCode.CANNOT_DELETE_FAILED_TODO);
        }

        todoRepository.delete(todo);
    }

    @Transactional
    public TodoResponse bringTodoToToday(Long todoId, Long userId) {
        Todo originalTodo = todoRepository.findById(todoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        Todo newTodo = Todo.builder()
                .description(originalTodo.getDescription())
                .category(originalTodo.getCategory())
                .date(LocalDate.now())
                .build();

        Todo saveTodo = todoRepository.save(newTodo);
        return TodoResponse.from(saveTodo);
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

    @Transactional
    public List<TodoListResponse> getTodoList(Long userId, LocalDate date, Long categoryId) {
        List<Todo> todos;

        if (categoryId == null) {
            todos = todoRepository.findAllByUserIdAndDate(userId, date);
        } else {
            todos = todoRepository.findAllByCategoryIdAndDate(categoryId, date);
        }

        return todos.stream()
                .map(TodoListResponse::from)
                .collect(Collectors.toList());
    }

}
