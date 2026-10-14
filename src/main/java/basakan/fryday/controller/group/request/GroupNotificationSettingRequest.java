package basakan.fryday.controller.group.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GroupNotificationSettingRequest {

    @NotNull(message = "알림 수신 여부는 필수입니다.")
    private Boolean enabled;

    public GroupNotificationSettingRequest(Boolean enabled) {
        this.enabled = enabled;
    }
}
