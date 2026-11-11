package basakan.fryday.controller.notification;

import basakan.fryday.common.response.ApiResponse;
import basakan.fryday.controller.notification.request.NotificationOpenRequest;
import basakan.fryday.service.notification.NotificationOpenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationOpenService notificationOpenService;

    @PostMapping("/opens")
    public ApiResponse<Void> recordOpen(@Valid @RequestBody NotificationOpenRequest request,
                                        @AuthenticationPrincipal Long userId) {
        notificationOpenService.recordOpen(request, userId);
        return ApiResponse.success(null, "알림 오픈이 기록되었습니다.");
    }
}
