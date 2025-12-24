package basakan.fryday.service.fcm;

import basakan.fryday.domain.user.UserDevice;
import basakan.fryday.repository.fcm.UserDeviceJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserDeviceScheduler {

    private final UserDeviceJpaRepository userDeviceRepository;

    /**
     * 비활성화된 디바이스 정리 (매일 새벽 2시)
     * isActive=false이고 마지막 사용이 90일 이상 지난 디바이스 삭제
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupInactiveDevices() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(90);
        List<UserDevice> oldDevices = userDeviceRepository.findByIsActiveFalseAndLastUsedAtBefore(threshold);

        if (oldDevices.isEmpty()) {
            return;
        }

        log.info("Cleaning up {} inactive devices (last used before {})", oldDevices.size(), threshold);

        userDeviceRepository.deleteAll(oldDevices);

        log.info("Cleanup completed");
    }
}
