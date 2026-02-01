package basakan.fryday.service.auth.dto;

import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.OnboardingStatus;
import basakan.fryday.domain.user.User;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record SocialLoginDto(
        Long userId,
        AuthProvider provider,
        User.Role role,
        String nickname,
        String email,
        LocalDateTime createdAt,
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
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .onboardingStatus(user.getOnboardingStatus())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .deviceId(deviceId)
                .build();
    }
}
