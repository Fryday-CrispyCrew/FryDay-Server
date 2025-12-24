package basakan.fryday.repository.auth;

import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<User, Long> {

    boolean existsByNickname(String nickname);

    Optional<User> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
