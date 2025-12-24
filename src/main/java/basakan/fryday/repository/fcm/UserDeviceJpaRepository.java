package basakan.fryday.repository.fcm;

import basakan.fryday.domain.user.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserDeviceJpaRepository extends JpaRepository<UserDevice, Long> {

    Optional<UserDevice> findByDeviceId(String deviceId);

    Optional<UserDevice> findByUserIdAndDeviceId(Long userId, String deviceId);

    List<UserDevice> findAllByUserId(Long userId);

    List<UserDevice> findByUserIdAndIsActiveTrue(Long userId);

    List<UserDevice> findByIsActiveFalseAndLastUsedAtBefore(LocalDateTime dateTime);

    void deleteByUserIdAndIsActiveFalse(Long userId);
}
