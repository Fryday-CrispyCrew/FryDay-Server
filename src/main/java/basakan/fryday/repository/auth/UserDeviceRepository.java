package basakan.fryday.repository.auth;

import basakan.fryday.domain.user.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * UserDevice Repository
 * - 기존 UserDeviceJpaRepository와 통합됨
 */
public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    List<UserDevice> findAllByUserIdAndIsActiveTrue(Long userId);

    Optional<UserDevice> findByDeviceId(String deviceId);

    Optional<UserDevice> findByUserIdAndDeviceId(Long userId, String deviceId);

    List<UserDevice> findAllByUserId(Long userId);

    List<UserDevice> findByUserIdAndIsActiveTrue(Long userId);

    List<UserDevice> findByIsActiveFalseAndLastUsedAtBefore(LocalDateTime dateTime);

    void deleteByUserIdAndIsActiveFalse(Long userId);

    List<UserDevice> findAllByUserIdAndIsActiveTrueAndPushNotificationAgreedTrue(Long userId);

    void deleteAllByUserId(Long userId);

    List<UserDevice> findAllByIsActiveTrueAndPushNotificationAgreedTrueAndFcmTokenIsNotNull();
}
