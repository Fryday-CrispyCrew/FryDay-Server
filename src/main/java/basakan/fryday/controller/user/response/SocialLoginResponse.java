package basakan.fryday.controller.user.response;

import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.OnboardingStatus;
import basakan.fryday.domain.user.User;
import basakan.fryday.service.auth.dto.SocialLoginDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialLoginResponse {

    private OnboardingStatus onboardingStatus;
    private String accessToken;
    private String refreshToken;
    private String deviceId;
    private UserInfo user;

    public static SocialLoginResponse from(SocialLoginDto dto) {
        UserInfo userInfo = UserInfo.builder()
                .id(dto.userId())
                .provider(dto.provider())
                .role(dto.role())
                .nickname(dto.nickname())
                .email(dto.email())
                .build();

        return SocialLoginResponse.builder()
                .onboardingStatus(dto.onboardingStatus())
                .accessToken(dto.accessToken())
                .refreshToken(dto.refreshToken())
                .deviceId(dto.deviceId())
                .user(userInfo)
                .build();
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserInfo {
        private Long id;
        private AuthProvider provider;
        private User.Role role;
        private String nickname;
        private String email;
    }
}
