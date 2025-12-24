package basakan.fryday.repository.auth;

import basakan.fryday.domain.user.Agreement;
import basakan.fryday.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgreementJpaRepository extends JpaRepository<Agreement, Long> {

    Optional<Agreement> findByUser(User user);
}
