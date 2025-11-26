package basakan.fryday.controller;

import basakan.fryday.controller.dto.*;
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

    @DeleteMapping("/{todoId}")
    public ApiResponse<Void> deleteTodo(@PathVariable Long todoId) {
        Long currentUserId = 1L; // TODO: 실제 인증 로직이 구현되면 현재 사용자 ID를 가져오도록 수정 필요

        todoService.deleteTodo(todoId, currentUserId);
        return ApiResponse.success(null, "투두가 삭제되었습니다.");
    }

    @PostMapping("{todoId}/bring-to-today")
    public ApiResponse<TodoResponse> bringTodoToToday(@PathVariable Long todoId) {
        Long currentUserId = 1L; // TODO: 실제 인증 로직이 구현되면 현재 사용자 ID를 가져오도록 수정 필요

        TodoResponse response = todoService.bringTodoToToday(todoId, currentUserId);
        return ApiResponse.success(response, "투두가 오늘로 가져와졌습니다.");
    }

    @PatchMapping("/{todoId}/tomorrow")
    public ApiResponse<TodoResponse> postponeToTomorrow(@PathVariable Long todoId) {
        Long currentUserId = 1L;

        TodoResponse response = todoService.postponeToTomorrow(todoId, currentUserId);
        return ApiResponse.success(response, "내일로 이동되었습니다.");
    }

    @PatchMapping("/{todoId}/today")
    public ApiResponse<TodoResponse> moveToToday(@PathVariable Long todoId) {
        Long currentUserId = 1L;

        TodoResponse response = todoService.moveToToday(todoId, currentUserId);
        return ApiResponse.success(response, "오늘로 이동되었습니다.");
    }

    @PatchMapping("/{todoId}/date")
    public ApiResponse<TodoResponse> updateTodoDate(
            @PathVariable Long todoId,
            @Valid @RequestBody TodoDateUpdateRequest request
            ) {
        Long currentUserId = 1L;

        TodoResponse response = todoService.updateTodoDate(todoId, currentUserId, request);
        return ApiResponse.success(response, "날짜가 변경되었습니다.");
    }
}
