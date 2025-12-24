package basakan.fryday.service.fcm;

import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.domain.user.UserDevice;
import basakan.fryday.repository.fcm.UserDeviceJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static basakan.fryday.common.ErrorCode.DEVICE_NOT_FOUND;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserDeviceReadService {

    private final UserDeviceJpaRepository userDeviceJpaRepository;

    public UserDevice findByDeviceId(String deviceId) {
        return userDeviceJpaRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new BusinessException(DEVICE_NOT_FOUND));
    }

    public Optional<UserDevice> findByUserIdAndDeviceId(Long userId, String deviceId) {
        return userDeviceJpaRepository.findByUserIdAndDeviceId(userId, deviceId);
    }
}
