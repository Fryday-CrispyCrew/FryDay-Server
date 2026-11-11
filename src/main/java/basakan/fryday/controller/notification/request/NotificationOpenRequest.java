package basakan.fryday.controller.notification.request;

import basakan.fryday.common.service.push.PushService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class NotificationOpenRequest {

    @NotNull(message = "알림을 연 상황은 필수입니다.")
    @Pattern(regexp = "foreground|background|cold_start", message = "지원하지 않는 알림 오픈 상황입니다.")
    private String trigger;

    // 고정 목록으로 막지 않아야 푸시 종류가 늘어도 이 API 를 고치지 않고 기록된다
    @Pattern(regexp = PushService.PUSH_TYPE_REGEX, message = "알림 종류 형식이 올바르지 않습니다.")
    private String type;

    @Positive(message = "그룹 ID 는 양수여야 합니다.")
    private Long groupId;

    @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
            message = "푸시 ID 형식이 올바르지 않습니다.")
    private String pushId;

    public NotificationOpenRequest(String trigger, String type, Long groupId, String pushId) {
        this.trigger = trigger;
        this.type = type;
        this.groupId = groupId;
        this.pushId = pushId;
    }
}
