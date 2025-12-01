package basakan.fryday.controller.auth.response;

import basakan.fryday.domain.auth.AuthProvider;
import basakan.fryday.domain.auth.LoginStatus;
import basakan.fryday.domain.auth.User;
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

    private LoginStatus loginStatus;
    private String accessToken;
    private String refreshToken;
    private UserInfo user;

    public static SocialLoginResponse from(SocialLoginDto dto) {
        UserInfo userInfo = UserInfo.builder()
                .id(dto.userId())
                .provider(dto.provider())
                .role(dto.role())
                .nickname(dto.nickname())
                .consentAgreed(dto.consentAgreed())
                .onboardingCompleted(dto.onboardingCompleted())
                .nicknameSet(dto.nicknameSet())
                .build();

        return SocialLoginResponse.builder()
                .loginStatus(dto.loginStatus())
                .accessToken(dto.accessToken())
                .refreshToken(dto.refreshToken())
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
        private boolean consentAgreed;
        private boolean onboardingCompleted;
        private boolean nicknameSet;
    }
}
