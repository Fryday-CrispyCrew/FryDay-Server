package basakan.fryday.service;

import basakan.fryday.api.dto.TodoResponse;
import basakan.fryday.api.dto.TodoSaveRequest;
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

}
