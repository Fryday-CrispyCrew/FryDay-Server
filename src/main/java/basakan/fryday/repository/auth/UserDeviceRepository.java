package basakan.fryday.repository.auth;

import basakan.fryday.domain.user.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    List<UserDevice> findAllByUserIdAndIsActiveTrue(Long userId);

    UserDevice findByUserIdAndDeviceId(Long userId, String deviceId);
}
