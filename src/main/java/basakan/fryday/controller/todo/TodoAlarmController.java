package basakan.fryday.controller.todo;

import basakan.fryday.common.response.MessageResponse;
import basakan.fryday.common.security.UserContext;
import basakan.fryday.controller.todo.request.TodoAlarmRequest;
import basakan.fryday.service.todo.TodoAlarmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoAlarmController {

    private final TodoAlarmService todoAlarmService;

    @PostMapping("/{todoId}/alarm")
    public ResponseEntity<MessageResponse> setTodoAlarm(
            @PathVariable Long todoId,
            @Valid @RequestBody TodoAlarmRequest request
    ) {
        Long userId = UserContext.getCurrentUserId();
        todoAlarmService.setTodoAlarm(todoId, userId, request.notifyAt());
        return ResponseEntity.ok(new MessageResponse("알림이 성공적으로 설정되었습니다."));
    }

    @DeleteMapping("/{todoId}/alarm")
    public ResponseEntity<MessageResponse> deleteTodoAlarm(@PathVariable Long todoId) {
        Long userId = UserContext.getCurrentUserId();
        todoAlarmService.deleteTodoAlarm(todoId, userId);
        return ResponseEntity.ok(new MessageResponse("알림이 성공적으로 삭제되었습니다."));
    }
}
