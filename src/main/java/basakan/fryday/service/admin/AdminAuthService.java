package basakan.fryday.service.admin;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.common.security.AdminJwtTokenProvider;
import basakan.fryday.controller.admin.response.AdminLoginResponse;
import basakan.fryday.domain.admin.AdminAccount;
import basakan.fryday.repository.admin.AdminAccountRepository;
import basakan.fryday.repository.admin.AdminLoginAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    // login_id 컬럼 정렬 규칙이 악센트 등도 같은 글자로 보므로, 다른 표기로 시도 횟수를 나눠 세지 못하게 형식을 제한한다
    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("[a-z0-9._-]{1,50}");

    // 없는 아이디도 BCrypt 비교를 거쳐서, 응답 시간으로 아이디 존재 여부를 알 수 없게 한다
    private static final String DUMMY_PASSWORD_HASH = "$2a$10$IPCa1srQDOhYTw0W1C30LO/n3S3qQ/QEDlcfbxrUrea12QLutxWsa";

    private final AdminAccountRepository adminAccountRepository;
    private final AdminLoginAttemptRepository adminLoginAttemptRepository;
    private final AdminJwtTokenProvider adminJwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AdminLoginResponse login(String loginId, String password) {
        // login_id 컬럼은 대소문자와 끝 공백을 구분하지 않으므로, 시도 횟수도 같은 기준으로 센다
        String normalizedLoginId = loginId.trim().toLowerCase(Locale.ROOT);
        if (!LOGIN_ID_PATTERN.matcher(normalizedLoginId).matches()) {
            throw new BusinessException(ErrorCode.ADMIN_LOGIN_FAILED);
        }
        if (!adminLoginAttemptRepository.tryAcquire(normalizedLoginId)) {
            throw new BusinessException(ErrorCode.ADMIN_LOGIN_LOCKED);
        }

        Optional<AdminAccount> account = adminAccountRepository.findByLoginId(normalizedLoginId);
        String passwordHash = account.map(AdminAccount::getPassword).orElse(DUMMY_PASSWORD_HASH);
        boolean matched = passwordEncoder.matches(password, passwordHash);

        // 아이디가 없는 경우와 비밀번호가 틀린 경우를 같은 에러로 응답한다
        if (account.isEmpty() || !matched) {
            throw new BusinessException(ErrorCode.ADMIN_LOGIN_FAILED);
        }

        AdminAccount admin = account.get();
        adminLoginAttemptRepository.reset(normalizedLoginId);
        admin.recordLogin(LocalDateTime.now(KOREA_ZONE));

        return new AdminLoginResponse(
                adminJwtTokenProvider.generateAccessToken(admin.getId()),
                adminJwtTokenProvider.getAccessTokenExpirationSeconds(),
                admin.getName()
        );
    }
}
