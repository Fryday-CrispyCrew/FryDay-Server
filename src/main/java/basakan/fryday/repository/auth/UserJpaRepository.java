package basakan.fryday.repository.auth;

import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<User, Long> {

    boolean existsByNickname(String nickname);

    Optional<User> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    @Query("SELECT u.id FROM User u WHERE u.accountStatus = 'ACTIVE'")
    List<Long> findAllActiveUserIds();
}
