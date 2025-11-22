package basakan.fryday.service;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.dto.TodoResponse;
import basakan.fryday.controller.dto.TodoSaveRequest;
import basakan.fryday.domain.Todo;
import basakan.fryday.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor

public class TodoService {

    private final TodoRepository todoRepository;

    @Transactional
    public TodoResponse saveTodo(TodoSaveRequest request) {
        Todo todo = request.toEntity();

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

}
