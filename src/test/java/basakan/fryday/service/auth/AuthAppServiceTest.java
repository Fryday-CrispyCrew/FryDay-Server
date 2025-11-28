package basakan.fryday.service.auth;

import basakan.fryday.common.client.MockSocialProviderClient;
import basakan.fryday.repository.auth.client.SocialProviderClientFactory;
import basakan.fryday.common.exception.auth.UserBlockedException;
import basakan.fryday.common.exception.auth.UserWithdrawnException;
import basakan.fryday.common.security.JwtTokenProvider;
import basakan.fryday.common.security.RefreshTokenRepository;
import basakan.fryday.domain.auth.AuthProvider;
import basakan.fryday.domain.auth.LoginStatus;
import basakan.fryday.domain.auth.SocialAccount;
import basakan.fryday.domain.auth.User;
import basakan.fryday.service.auth.dto.SocialLoginDto;
import basakan.fryday.service.auth.dto.SocialLoginServiceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthAppServiceTest {

    @Mock
    private AuthReadService authReadService;

    @Mock
    private AuthWriteService authWriteService;

    @Mock
    private SocialProviderClientFactory providerClientFactory;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private AuthAppService authAppService;

    private SocialLoginServiceDto serviceDto;

    @BeforeEach
    void setUp() {
        serviceDto = new SocialLoginServiceDto(
                AuthProvider.KAKAO,
                "mock-access-token",
                null
        );
    }

    @Test
    @DisplayName("최초 로그인 시 새로운 유저를 생성하고 NEEDS_CONSENT 상태를 반환한다")
    void socialLogin_NewUser_ReturnsNeedsConsent() {
        // given
        MockSocialProviderClient mockClient = new MockSocialProviderClient(AuthProvider.KAKAO);
        given(providerClientFactory.getClient(AuthProvider.KAKAO)).willReturn(mockClient);
        given(authReadService.findSocialAccount(any(), any())).willReturn(Optional.empty());

        User newUser = User.createNewUser();
        given(authWriteService.createUserWithSocialAccount(any())).willReturn(newUser);
        given(jwtTokenProvider.generateAccessToken(any(), any())).willReturn("access-token");
        given(refreshTokenRepository.generateAndSave(any())).willReturn("refresh-token");

        // when
        SocialLoginDto result = authAppService.socialLogin(serviceDto);

        // then
        assertThat(result.loginStatus()).isEqualTo(LoginStatus.NEEDS_CONSENT);
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.consentAgreed()).isFalse();
        assertThat(result.onboardingCompleted()).isFalse();
        assertThat(result.nicknameSet()).isFalse();

        verify(authWriteService, times(1)).createUserWithSocialAccount(any());
    }

    @Test
    @DisplayName("기존 유저 로그인 시 유저 정보와 토큰을 반환한다")
    void socialLogin_ExistingUser_ReturnsUserAndTokens() {
        // given
        MockSocialProviderClient mockClient = new MockSocialProviderClient(AuthProvider.KAKAO);
        given(providerClientFactory.getClient(AuthProvider.KAKAO)).willReturn(mockClient);

        User existingUser = User.builder()
                .consentAgreed(true)
                .onboardingCompleted(true)
                .nicknameSet(true)
                .nickname("영오")
                .status(User.Status.ACTIVE)
                .role(User.Role.USER)
                .build();

        SocialAccount socialAccount = SocialAccount.builder()
                .provider(AuthProvider.KAKAO)
                .providerUserId("mock-provider-user-id")
                .user(existingUser)
                .build();

        given(authReadService.findSocialAccount(any(), any())).willReturn(Optional.of(socialAccount));
        given(jwtTokenProvider.generateAccessToken(any(), any())).willReturn("access-token");
        given(refreshTokenRepository.generateAndSave(any())).willReturn("refresh-token");

        // when
        SocialLoginDto result = authAppService.socialLogin(serviceDto);

        // then
        assertThat(result.loginStatus()).isEqualTo(LoginStatus.COMPLETED);
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.consentAgreed()).isTrue();
        assertThat(result.onboardingCompleted()).isTrue();
        assertThat(result.nicknameSet()).isTrue();
        assertThat(result.nickname()).isEqualTo("영오");

        verify(authWriteService, never()).createUserWithSocialAccount(any());
    }

    @Test
    @DisplayName("차단된 유저가 로그인 시도 시 UserBlockedException을 던진다")
    void socialLogin_BlockedUser_ThrowsUserBlockedException() {
        // given
        MockSocialProviderClient mockClient = new MockSocialProviderClient(AuthProvider.KAKAO);
        given(providerClientFactory.getClient(AuthProvider.KAKAO)).willReturn(mockClient);

        User blockedUser = User.builder()
                .consentAgreed(true)
                .onboardingCompleted(true)
                .nicknameSet(true)
                .status(User.Status.BLOCKED)
                .role(User.Role.USER)
                .build();

        SocialAccount socialAccount = SocialAccount.builder()
                .provider(AuthProvider.KAKAO)
                .providerUserId("mock-provider-user-id")
                .user(blockedUser)
                .build();

        given(authReadService.findSocialAccount(any(), any())).willReturn(Optional.of(socialAccount));

        // when & then
        assertThatThrownBy(() -> authAppService.socialLogin(serviceDto))
                .isInstanceOf(UserBlockedException.class);

        verify(jwtTokenProvider, never()).generateAccessToken(any(), any());
        verify(refreshTokenRepository, never()).generateAndSave(anyLong());
    }

    @Test
    @DisplayName("탈퇴한 유저가 로그인 시도 시 UserWithdrawnException을 던진다")
    void socialLogin_WithdrawnUser_ThrowsUserWithdrawnException() {
        // given
        MockSocialProviderClient mockClient = new MockSocialProviderClient(AuthProvider.KAKAO);
        given(providerClientFactory.getClient(AuthProvider.KAKAO)).willReturn(mockClient);

        User withdrawnUser = User.builder()
                .consentAgreed(true)
                .onboardingCompleted(true)
                .nicknameSet(true)
                .status(User.Status.WITHDRAWN)
                .role(User.Role.USER)
                .build();

        SocialAccount socialAccount = SocialAccount.builder()
                .provider(AuthProvider.KAKAO)
                .providerUserId("mock-provider-user-id")
                .user(withdrawnUser)
                .build();

        given(authReadService.findSocialAccount(any(), any())).willReturn(Optional.of(socialAccount));

        // when & then
        assertThatThrownBy(() -> authAppService.socialLogin(serviceDto))
                .isInstanceOf(UserWithdrawnException.class);

        verify(jwtTokenProvider, never()).generateAccessToken(any(), any());
        verify(refreshTokenRepository, never()).generateAndSave(anyLong());
    }
}
