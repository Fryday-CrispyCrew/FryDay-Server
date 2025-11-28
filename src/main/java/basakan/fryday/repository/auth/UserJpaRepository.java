package basakan.fryday.repository.auth;

import basakan.fryday.domain.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<User, Long> {
}
