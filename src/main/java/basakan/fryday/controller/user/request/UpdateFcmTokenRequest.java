package basakan.fryday.controller.user.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateFcmTokenRequest(
        @NotBlank(message = "디바이스 ID는 필수입니다.")
        String deviceId,

        @NotBlank(message = "FCM 토큰은 필수입니다.")
        @Size(max = 500, message = "FCM 토큰은 최대 500자까지 입력 가능합니다.")
        String fcmToken
) {
}
