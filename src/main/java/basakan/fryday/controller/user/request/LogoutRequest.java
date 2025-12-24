package basakan.fryday.controller.user.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LogoutRequest(
        @NotBlank(message = "디바이스 ID는 필수입니다.")
        @Size(max = 36, message = "디바이스 ID는 최대 36자까지 입력 가능합니다.")
        String deviceId,

        @NotBlank(message = "Refresh Token은 필수입니다.")
        String refreshToken
) {
}
