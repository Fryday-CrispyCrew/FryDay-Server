package basakan.fryday.service.auth.dto;

import basakan.fryday.domain.user.AuthProvider;

public record SocialLoginServiceDto(
        AuthProvider provider,
        String accessToken,
        String idToken,
        String deviceId,
        String deviceType,
        String deviceName,
        String fcmToken
) {
}
