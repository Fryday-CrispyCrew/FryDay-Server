package basakan.fryday.service.fcm;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.domain.user.UserDevice;
import basakan.fryday.repository.auth.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

@Slf4j
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

    @Transactional(propagation = REQUIRES_NEW)
    public void deactivateDeviceById(Long deviceId) {
        userDeviceRepository.findById(deviceId)
                .ifPresent(device -> {
                    device.deactivate();
                    log.info("Device deactivated: deviceId={}", deviceId);
                });
    }

    public void deactivateAllByUserId(Long userId) {
        userDeviceRepository.findAllByUserId(userId)
                .forEach(UserDevice::deactivate);
    }
}
