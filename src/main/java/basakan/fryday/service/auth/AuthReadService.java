package basakan.fryday.service.auth;

import basakan.fryday.domain.auth.AuthProvider;
import basakan.fryday.domain.auth.SocialAccount;
import basakan.fryday.repository.auth.SocialAccountJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthReadService {

    private final SocialAccountJpaRepository socialAccountRepository;

    public Optional<SocialAccount> findSocialAccount(AuthProvider provider, String providerUserId) {
        return socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId);
    }
}
