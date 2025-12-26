package basakan.fryday.repository.auth;

import basakan.fryday.domain.user.Agreement;
import basakan.fryday.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AgreementJpaRepository extends JpaRepository<Agreement, Long> {

    Optional<Agreement> findByUser(User user);

    @Query("SELECT a.user FROM Agreement a " +
            "WHERE a.pushNotificationAgreed = true " +
            "AND a.user.accountStatus = 'ACTIVE'")
    List<User> findAllUsersWithPushNotificationEnabled();
}
