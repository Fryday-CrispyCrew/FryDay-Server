package basakan.fryday.service.user;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.domain.user.Agreement;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.auth.AgreementJpaRepository;
import basakan.fryday.repository.auth.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserReadService {

    private final UserJpaRepository userJpaRepository;
    private final AgreementJpaRepository agreementRepository;

    public User findById(Long userId) {
        return userJpaRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    public boolean existsByNickname(String nickname) {
        return userJpaRepository.existsByNickname(nickname);
    }

    public Optional<User> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId) {
        return userJpaRepository.findByProviderAndProviderUserId(provider, providerUserId);
    }

    public Optional<User> findActiveUserByProviderAndProviderUserId(AuthProvider provider, String providerUserId) {
        return userJpaRepository.findByProviderAndProviderUserIdAndAccountStatus(
                provider, providerUserId, User.AccountStatus.ACTIVE);
    }

    public Optional<User> findWithdrawnUserByProviderAndProviderUserId(AuthProvider provider, String providerUserId) {
        return userJpaRepository.findByProviderAndProviderUserIdAndAccountStatus(
                provider, providerUserId, User.AccountStatus.WITHDRAWN);
    }

    public Optional<Agreement> findAgreementByUser(User user) {
        return agreementRepository.findByUser(user);
    }
}
