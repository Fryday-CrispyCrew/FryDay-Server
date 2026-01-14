package basakan.fryday.controller.todo;

import basakan.fryday.common.response.ApiResponse;
import basakan.fryday.controller.todo.request.*;
import basakan.fryday.controller.dto.OrderUpdateRequest;
import basakan.fryday.controller.todo.response.CharacterStatusResponse;
import basakan.fryday.controller.todo.response.MemoResponse;
import basakan.fryday.controller.todo.response.TodoListResponse;
import basakan.fryday.controller.todo.response.TodoResponse;
import basakan.fryday.controller.todo.response.TodoDetailResponse;
import basakan.fryday.service.todo.RecurrenceService;
import basakan.fryday.service.todo.TodoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/todos")
public class TodoController {

    private final TodoService todoService;
    private final RecurrenceService recurrenceService;

    @GetMapping
    public ApiResponse<List<TodoListResponse>> getTodoList(
            @RequestParam LocalDate date,
            @RequestParam(required = false) Long categoryId,
            @AuthenticationPrincipal Long userId
    ) {

        List<TodoListResponse> responses = todoService.getTodoList(userId, date, categoryId);
        return ApiResponse.success(responses);
    }

    @GetMapping("/{todoId}")
    public ApiResponse<TodoDetailResponse> getTodoDetail(
            @PathVariable Long todoId,
            @AuthenticationPrincipal Long userId
    ) {
        TodoDetailResponse response = todoService.getTodoDetail(todoId, userId);
        return ApiResponse.success(response);
    }

    @PostMapping
    public ApiResponse<TodoResponse> createTodo(@Valid @RequestBody TodoSaveRequest request,
                                                @AuthenticationPrincipal Long userId) {
        TodoResponse response = todoService.saveTodo(request, userId);
        return ApiResponse.success(response);
    }

    @PatchMapping("/{todoId}/description")
    public ApiResponse<TodoResponse> updateDescription(
            @PathVariable Long todoId,
            @Valid @RequestBody TodoDescriptionUpdateRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        TodoResponse response = todoService.updateDescription(todoId, userId, request);
        return ApiResponse.success(response, "내용이 수정되었습니다.");
    }

    @PostMapping("/{todoId}/completion")
    public ApiResponse<TodoResponse> toggleTodoCompletion(@PathVariable Long todoId, @AuthenticationPrincipal Long userId) {

        TodoResponse response = todoService.toggleTodoCompletion(todoId, userId);
        return ApiResponse.success(response);
    }

    @PatchMapping("/{todoId}/memo")
    public ApiResponse<MemoResponse> updateMemo(
            @PathVariable Long todoId,
            @Valid @RequestBody MemoRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        MemoResponse response = todoService.updateMemo(todoId, userId, request);
        return ApiResponse.success(response, "메모가 저장되었습니다.");
    }

    @DeleteMapping("/{todoId}")
    public ApiResponse<Void> deleteTodo(@PathVariable Long todoId,
                                        @AuthenticationPrincipal Long userId) {
        todoService.deleteTodo(todoId, userId);
        return ApiResponse.success(null, "투두가 삭제되었습니다.");
    }

    @PatchMapping("/{todoId}/tomorrow")
    public ApiResponse<TodoResponse> postponeToTomorrow(@PathVariable Long todoId,
                                                        @AuthenticationPrincipal Long userId) {
        TodoResponse response = todoService.postponeToTomorrow(todoId, userId);
        return ApiResponse.success(response, "내일로 이동되었습니다.");
    }

    @PatchMapping("/{todoId}/today")
    public ApiResponse<TodoResponse> moveToToday(@PathVariable Long todoId,
                                                 @AuthenticationPrincipal Long userId) {
        TodoResponse response = todoService.moveToToday(todoId, userId);
        return ApiResponse.success(response, "오늘로 이동되었습니다.");
    }

    @PatchMapping("/{todoId}/date")
    public ApiResponse<TodoResponse> updateTodoDate(
            @PathVariable Long todoId,
            @Valid @RequestBody TodoDateUpdateRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        TodoResponse response = todoService.updateTodoDate(todoId, userId, request);
        return ApiResponse.success(response, "날짜가 변경되었습니다.");
    }

    @PatchMapping("/{todoId}/category")
    public ApiResponse<TodoResponse> updateCategory(
            @PathVariable Long todoId,
            @Valid @RequestBody TodoCategoryUpdateRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        TodoResponse response = todoService.updateCategory(todoId, userId, request);
        return ApiResponse.success(response, "카테고리가 변경되었습니다.");
    }

    @PatchMapping("/reorder")
    public ApiResponse<Void> reorderTodos(
            @RequestParam LocalDate date,
            @Valid @RequestBody OrderUpdateRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        todoService.reorderTodos(userId, date, request);
        return ApiResponse.success(null, "투두 순서가 변경되었습니다.");
    }

    @GetMapping("/character-status")
    public ApiResponse<CharacterStatusResponse> getCharacterStatus(
            @RequestParam LocalDate date,
            @AuthenticationPrincipal Long userId
    ) {
        CharacterStatusResponse response = todoService.getDailyCharacterStatus(userId, date);
        return ApiResponse.success(response);
    }

    @PostMapping("/recurrence")
    public ApiResponse<TodoResponse> createRecurringTodo(@Valid @RequestBody RecurrenceCreateRequest request,
                                                         @AuthenticationPrincipal Long userId) {
        TodoResponse response = recurrenceService.createRecurrence(userId, request);
        return ApiResponse.success(response, "반복 투두가 생성되었습니다.");
    }

    @DeleteMapping("/recurrence/{recurrenceId}")
    public ApiResponse<Void> deleteRecurrence(@PathVariable Long recurrenceId,
                                              @AuthenticationPrincipal Long userId) {
        recurrenceService.deleteRecurrence(recurrenceId, userId);
        return ApiResponse.success(null, "반복 투두가 모두 해제(삭제)되었습니다.");
    }

    @PostMapping("/recurrence/{recurrenceId}/completion")
    public ApiResponse<TodoResponse> toggleRecurrenceOccurrenceCompletion(
            @PathVariable Long recurrenceId,
            @Valid @RequestBody RecurrenceOccurrenceCompletionRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        TodoResponse response = recurrenceService.toggleRecurrenceOccurrenceCompletion(recurrenceId, request.getOccurrenceDate(), userId);
        return ApiResponse.success(response, "반복 투두 완료 상태가 변경되었습니다.");
    }

    // 현재 기획상은 미존재, 나중에 들어올 것 같음
//    @PostMapping("/recurrence/{recurrenceId}/cancel")
//    public ApiResponse<Void> cancelRecurrenceOccurrence(
//            @PathVariable Long recurrenceId,
//            @Valid @RequestBody RecurrenceOccurrenceCancelRequest request,
//            @AuthenticationPrincipal Long userId
//    ) {
//        recurrenceService.cancelRecurrenceOccurrence(recurrenceId, request.getOccurrenceDate(), userId);
//        return ApiResponse.success(null, "반복 투두 회차가 제외되었습니다.");
//    }

    @PostMapping("/recurrence/{recurrenceId}/detach")
    public ApiResponse<TodoResponse> detachRecurrenceOccurrence(
            @PathVariable Long recurrenceId,
            @Valid @RequestBody RecurrenceOccurrenceDetachRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        TodoResponse response = recurrenceService.detachRecurrenceOccurrence(
                recurrenceId, request.getOccurrenceDate(), request.getNewDate(), userId);
        return ApiResponse.success(response, "반복 투두가 단건 투두로 분리되었습니다.");
    }

    @PatchMapping("/recurrence/{recurrenceId}")
    public ApiResponse<Void> updateRecurrence(
            @PathVariable Long recurrenceId,
            @Valid @RequestBody RecurrenceUpdateRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        recurrenceService.updateRecurrence(recurrenceId, request, userId);
        return ApiResponse.success(null, "반복 투두 규칙이 수정되었습니다.");
    }
}
