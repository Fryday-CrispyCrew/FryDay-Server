package basakan.fryday.service.auth.dto;

import basakan.fryday.domain.auth.AuthProvider;
import basakan.fryday.domain.auth.LoginStatus;
import basakan.fryday.domain.auth.User;
import lombok.Builder;

@Builder
public record SocialLoginDto(
        Long userId,
        AuthProvider provider,
        User.Role role,
        String nickname,
        boolean consentAgreed,
        boolean onboardingCompleted,
        boolean nicknameSet,
        LoginStatus loginStatus,
        String accessToken,
        String refreshToken
) {
    public static SocialLoginDto from(User user, AuthProvider provider, String accessToken, String refreshToken) {
        return SocialLoginDto.builder()
                .userId(user.getId())
                .provider(provider)
                .role(user.getRole())
                .nickname(user.getNickname())
                .consentAgreed(user.isConsentAgreed())
                .onboardingCompleted(user.isOnboardingCompleted())
                .nicknameSet(user.isNicknameSet())
                .loginStatus(user.calculateLoginStatus())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
}
