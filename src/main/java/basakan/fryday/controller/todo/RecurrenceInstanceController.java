package basakan.fryday.controller.todo;

import basakan.fryday.common.response.ApiResponse;
import basakan.fryday.controller.todo.request.InstanceDeleteRequest;
import basakan.fryday.controller.todo.request.InstanceEditRequest;
import basakan.fryday.service.todo.RecurrenceInstanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/todos/instances")
public class RecurrenceInstanceController {

    private final RecurrenceInstanceService recurrenceInstanceService;

    @PutMapping("/{instanceId}/edit")
    public ApiResponse<Void> editInstance(
            @PathVariable long instanceId,
            @RequestBody @Valid InstanceEditRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        recurrenceInstanceService.edit(instanceId, request.getScope(), request.getPayload(), userId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{instanceId}")
    public ApiResponse<Void> deleteInstance(
            @PathVariable long instanceId,
            @RequestBody @Valid InstanceDeleteRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        recurrenceInstanceService.delete(instanceId, request.getScope(), userId);
        return ApiResponse.success(null);
    }
}
