package basakan.fryday.service.fcm;

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

    public void deactivateDevice(UserDevice userDevice) {
        userDevice.deactivate();
    }

    public void deactivateAllByUserId(Long userId) {
        userDeviceRepository.findAllByUserId(userId)
                .forEach(UserDevice::deactivate);
    }
}
