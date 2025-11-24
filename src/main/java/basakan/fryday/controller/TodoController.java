package basakan.fryday.controller;

import basakan.fryday.controller.dto.MemoResponse;
import basakan.fryday.controller.dto.MemoRequest;
import basakan.fryday.controller.dto.TodoResponse;
import basakan.fryday.controller.dto.TodoSaveRequest;
import basakan.fryday.common.response.ApiResponse;
import basakan.fryday.service.TodoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/{todoId}/completion")
    public ApiResponse<TodoResponse> toggleTodoCompletion(@PathVariable Long todoId) {
        Long currentUserId = 1L; // TODO: 실제 인증 로직이 구현되면 현재 사용자 ID를 가져오도록 수정 필요

        TodoResponse response = todoService.toggleTodoCompletion(todoId, currentUserId);
        return ApiResponse.success(response);
    }

    @PatchMapping("/{todoId}/memo")
    public ApiResponse<MemoResponse> updateMemo(
            @PathVariable Long todoId,
            @Valid @RequestBody MemoRequest request
    ) {
        Long currentUserId = 1L; // TODO: 실제 인증 로직이 구현되면 현재 사용자 ID를 가져오도록 수정 필요

        MemoResponse response = todoService.updateMemo(todoId, currentUserId, request);
        return ApiResponse.success(response, "메모가 저장되었습니다.");
    }
}
