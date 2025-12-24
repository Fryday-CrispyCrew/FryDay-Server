package basakan.fryday.service.auth.dto;

import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.OnboardingStatus;
import basakan.fryday.domain.user.User;
import lombok.Builder;

@Builder
public record SocialLoginDto(
        Long userId,
        AuthProvider provider,
        User.Role role,
        String nickname,
        OnboardingStatus onboardingStatus,
        String accessToken,
        String refreshToken,
        String deviceId
) {
    public static SocialLoginDto from(User user, AuthProvider provider, String accessToken, String refreshToken, String deviceId) {
        return SocialLoginDto.builder()
                .userId(user.getId())
                .provider(provider)
                .role(user.getRole())
                .nickname(user.getNickname())
                .onboardingStatus(user.getOnboardingStatus())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .deviceId(deviceId)
                .build();
    }
}
