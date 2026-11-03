package basakan.fryday.service.admin;

import basakan.fryday.common.service.push.PushService;
import basakan.fryday.common.service.push.PushType;
import basakan.fryday.domain.user.UserDevice;
import basakan.fryday.repository.auth.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPushService {

    private static final int BATCH_SIZE = 500;

    private final UserDeviceRepository userDeviceRepository;
    private final PushService pushService;

    @Transactional(readOnly = true)
    public int broadcastPush(String title, String body) {
        // 거의 쓰지 않는 기능이라 유저 단위 발송 기록 없이, 발송 한 번에 pushId 하나만 붙인다
        Map<String, String> data = Map.of(
                PushService.TYPE_KEY, PushType.ADMIN_BROADCAST.name(),
                PushService.PUSH_ID_KEY, UUID.randomUUID().toString());
        Set<String> sentTokens = new HashSet<>();
        int successCount = 0;

        Slice<UserDevice> slice = userDeviceRepository
                .findAllByIsActiveTrueAndPushNotificationAgreedTrueAndFcmTokenIsNotNull(PageRequest.of(0, BATCH_SIZE));

        while (!slice.isEmpty()) {
            for (UserDevice device : slice.getContent()) {
                String token = device.getFcmToken();
                if (token == null || token.isBlank() || !sentTokens.add(token)) {
                    continue;
                }
                try {
                    pushService.sendToToken(token, title, body, data);
                    successCount++;
                } catch (Exception e) {
                    log.warn("전체 푸시 발송 실패: error={}", e.getMessage());
                }
            }

            if (!slice.hasNext()) {
                break;
            }
            slice = userDeviceRepository
                    .findAllByIsActiveTrueAndPushNotificationAgreedTrueAndFcmTokenIsNotNull(slice.nextPageable());
        }

        log.info("전체 푸시 발송 완료: 고유 토큰 {}개 중 {}개 성공", sentTokens.size(), successCount);
        return successCount;
    }
}
