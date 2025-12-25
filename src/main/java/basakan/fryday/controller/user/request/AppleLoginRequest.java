package basakan.fryday.controller.user.request;

import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.service.auth.dto.SocialLoginServiceDto;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AppleLoginRequest {

    @NotBlank(message = "ID Token은 필수입니다")
    private String idToken;

    private String authorizationCode;  // 선택, Refresh Token 갱신 시 사용

    @NotBlank(message = "DeviceId는 필수입니다")
    private String deviceId;

    private String deviceType;  // iOS, Android, Web

    private String deviceName;  // "iPhone 14 Pro", "iPad Pro" 등

    private String fcmToken;

    public SocialLoginServiceDto toServiceDto() {
        return new SocialLoginServiceDto(
                AuthProvider.APPLE,
                null,  // accessToken은 사용하지 않음
                idToken,
                deviceId,
                deviceType,
                deviceName,
                fcmToken
        );
    }
}
