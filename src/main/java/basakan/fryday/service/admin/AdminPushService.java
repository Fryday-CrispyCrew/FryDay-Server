package basakan.fryday.service.admin;

import basakan.fryday.common.service.push.PushService;
import basakan.fryday.domain.user.UserDevice;
import basakan.fryday.repository.auth.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPushService {

    private final UserDeviceRepository userDeviceRepository;
    private final PushService pushService;

    @Transactional(readOnly = true)
    public int broadcastPush(String title, String body) {
        List<UserDevice> devices = userDeviceRepository
                .findAllByIsActiveTrueAndPushNotificationAgreedTrueAndFcmTokenIsNotNull();

        if (devices.isEmpty()) {
            log.info("전체 푸시 발송 대상 디바이스가 없습니다.");
            return 0;
        }

        int successCount = 0;
        for (UserDevice device : devices) {
            try {
                pushService.sendToToken(device.getFcmToken(), title, body);
                successCount++;
            } catch (Exception e) {
                log.warn("전체 푸시 발송 실패: fcmToken={}, error={}", device.getFcmToken(), e.getMessage());
            }
        }

        log.info("전체 푸시 발송 완료: 총 {}대 중 {}대 성공", devices.size(), successCount);
        return successCount;
    }
}
