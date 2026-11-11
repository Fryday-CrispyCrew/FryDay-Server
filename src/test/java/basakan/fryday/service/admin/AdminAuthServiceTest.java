package basakan.fryday.service.admin;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.common.security.AdminJwtTokenProvider;
import basakan.fryday.controller.admin.response.AdminLoginResponse;
import basakan.fryday.domain.admin.AdminAccount;
import basakan.fryday.repository.admin.AdminAccountRepository;
import basakan.fryday.repository.admin.AdminLoginAttemptRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    @Mock private AdminAccountRepository adminAccountRepository;
    @Mock private AdminLoginAttemptRepository adminLoginAttemptRepository;
    @Mock private AdminJwtTokenProvider adminJwtTokenProvider;
    @Spy private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @InjectMocks
    private AdminAuthService adminAuthService;

    @Test
    @DisplayName("비밀번호가 맞으면 토큰을 발급하고 실패 횟수를 초기화한다")
    void login() {
        // given
        AdminAccount admin = admin("admin", "password");
        given(adminLoginAttemptRepository.tryAcquire("admin")).willReturn(true);
        given(adminAccountRepository.findByLoginId("admin")).willReturn(Optional.of(admin));
        given(adminJwtTokenProvider.generateAccessToken(1L)).willReturn("admin-access-token");
        given(adminJwtTokenProvider.getAccessTokenExpirationSeconds()).willReturn(43200L);

        // when
        AdminLoginResponse response = adminAuthService.login("admin", "password");

        // then
        assertThat(response.getAccessToken()).isEqualTo("admin-access-token");
        assertThat(response.getExpiresIn()).isEqualTo(43200L);
        assertThat(response.getName()).isEqualTo("운영자");
        assertThat(admin.getLastLoginAt()).isNotNull();
        verify(adminLoginAttemptRepository).reset("admin");
    }

    @Test
    @DisplayName("비밀번호가 틀리면 로그인 실패 에러를 던지고 시도 횟수를 초기화하지 않는다")
    void wrongPassword() {
        // given
        AdminAccount admin = admin("admin", "password");
        given(adminLoginAttemptRepository.tryAcquire("admin")).willReturn(true);
        given(adminAccountRepository.findByLoginId("admin")).willReturn(Optional.of(admin));

        // when & then
        assertThatThrownBy(() -> adminAuthService.login("admin", "wrong"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_LOGIN_FAILED);
        verify(adminLoginAttemptRepository, never()).reset(anyString());
        verify(adminJwtTokenProvider, never()).generateAccessToken(1L);
    }

    @Test
    @DisplayName("없는 아이디도 비밀번호를 비교한 뒤 비밀번호가 틀린 경우와 같은 에러를 던진다")
    void unknownLoginId() {
        // given
        given(adminLoginAttemptRepository.tryAcquire("nobody")).willReturn(true);
        given(adminAccountRepository.findByLoginId("nobody")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminAuthService.login("nobody", "password"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_LOGIN_FAILED);
        verify(passwordEncoder).matches(eq("password"), anyString());
    }

    @Test
    @DisplayName("아이디의 대소문자와 앞뒤 공백을 정리해서 같은 아이디로 시도 횟수를 센다")
    void normalizesLoginId() {
        // given
        given(adminLoginAttemptRepository.tryAcquire("admin")).willReturn(true);
        given(adminAccountRepository.findByLoginId("admin")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminAuthService.login(" Admin ", "password"))
                .isInstanceOf(BusinessException.class);
        verify(adminLoginAttemptRepository).tryAcquire("admin");
    }

    @Test
    @DisplayName("잠긴 아이디는 비밀번호를 확인하지 않고 잠금 에러를 던진다")
    void locked() {
        // given
        given(adminLoginAttemptRepository.tryAcquire("admin")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> adminAuthService.login("admin", "password"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_LOGIN_LOCKED);
        verify(adminAccountRepository, never()).findByLoginId(anyString());
    }

    @Test
    @DisplayName("영문 소문자, 숫자, . _ - 이외의 문자가 들어간 아이디는 조회하지 않고 로그인 실패 에러를 던진다")
    void rejectsInvalidLoginId() {
        // when & then
        assertThatThrownBy(() -> adminAuthService.login("ádmin", "password"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_LOGIN_FAILED);
        verify(adminLoginAttemptRepository, never()).tryAcquire(anyString());
        verify(adminAccountRepository, never()).findByLoginId(anyString());
    }

    private AdminAccount admin(String loginId, String rawPassword) {
        AdminAccount admin = AdminAccount.builder()
                .loginId(loginId)
                .password(passwordEncoder.encode(rawPassword))
                .name("운영자")
                .build();
        ReflectionTestUtils.setField(admin, "id", 1L);
        return admin;
    }
}
