package basakan.fryday.repository.auth;

import basakan.fryday.domain.auth.AuthProvider;
import basakan.fryday.domain.auth.SocialAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SocialAccountJpaRepository extends JpaRepository<SocialAccount, Long> {

    Optional<SocialAccount> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
