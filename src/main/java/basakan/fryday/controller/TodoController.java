package basakan.fryday.controller;

import basakan.fryday.controller.dto.TodoResponse;
import basakan.fryday.controller.dto.TodoSaveRequest;
import basakan.fryday.common.response.ApiResponse;
import basakan.fryday.service.TodoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/todos")
public class TodoController {

    private final TodoService todoService;

    @PostMapping
    public ApiResponse<TodoResponse> createTodo(@Valid @RequestBody TodoSaveRequest request) {
        TodoResponse response = todoService.saveTodo(request);

        return ApiResponse.success(response);
    }
}
