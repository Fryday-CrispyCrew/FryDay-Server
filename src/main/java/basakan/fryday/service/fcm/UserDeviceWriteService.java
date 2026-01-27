package basakan.fryday.service.fcm;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.domain.user.UserDevice;
import basakan.fryday.repository.auth.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserDeviceWriteService {

    private final UserDeviceRepository userDeviceRepository;

    public void createOrActivateDevice(Long userId, String deviceId, String deviceType, String deviceName, String fcmToken) {
        UserDevice userDevice = UserDevice.builder()
                .userId(userId)
                .deviceId(deviceId)
                .deviceType(deviceType)
                .deviceName(deviceName)
                .fcmToken(fcmToken)
                .build();

        userDeviceRepository.save(userDevice);
    }

    public void activateDevice(UserDevice userDevice) {
        userDevice.activate();
    }

    public void updateFcmToken(UserDevice userDevice, String fcmToken) {
        userDevice.updateFcmToken(fcmToken);
    }

    public void updatePushNotificationAgreement(Long userId, String deviceId, boolean agreed) {
        UserDevice device = userDeviceRepository.findByUserIdAndDeviceId(userId, deviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));
        device.updatePushNotificationAgreement(agreed);
    }

    public void deactivateDevice(UserDevice userDevice) {
        userDevice.deactivate();
    }

    public void deactivateAllByUserId(Long userId) {
        userDeviceRepository.findAllByUserId(userId)
                .forEach(UserDevice::deactivate);
    }
}
