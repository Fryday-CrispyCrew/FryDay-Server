package basakan.fryday.controller.user.request;

import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.service.auth.dto.SocialLoginServiceDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SocialLoginRequest {

    @NotNull(message = "Provider는 필수입니다")
    private AuthProvider provider;

    @NotBlank(message = "AccessToken은 필수입니다")
    private String accessToken;

    private String idToken;

    @NotBlank(message = "DeviceId는 필수입니다")
    private String deviceId;

    private String deviceType;  // iOS, Android, Web

    private String deviceName;  // "iPhone 14 Pro", "Galaxy S23" 등

    private String fcmToken;

    public SocialLoginServiceDto toServiceDto() {
        return new SocialLoginServiceDto(
                provider,
                accessToken,
                idToken,
                deviceId,
                deviceType,
                deviceName,
                fcmToken
        );
    }
}
