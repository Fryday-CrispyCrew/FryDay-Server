package basakan.fryday.controller.admin;

import basakan.fryday.common.response.ApiResponse;
import basakan.fryday.controller.admin.request.BroadcastPushRequest;
import basakan.fryday.controller.admin.response.BroadcastPushResponse;
import basakan.fryday.service.admin.AdminPushService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminPushController {

    private final AdminPushService adminPushService;

    @PostMapping("/push")
    public ApiResponse<BroadcastPushResponse> broadcastPush(
            @Valid @RequestBody BroadcastPushRequest request
    ) {
        int sentCount = adminPushService.broadcastPush(request.getTitle(), request.getBody());
        return ApiResponse.success(new BroadcastPushResponse(sentCount));
    }
}
