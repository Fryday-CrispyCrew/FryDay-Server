package basakan.fryday.common.service.push;

import basakan.fryday.domain.user.User;
import basakan.fryday.domain.user.UserDevice;
import basakan.fryday.repository.auth.UserDeviceRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmPushService implements PushService {

    private static final int MAX_RETRIES = 1;
    private static final long RETRY_DELAY_MS = 500L;

    private final UserDeviceRepository userDeviceRepository;

    @Value("${fcm.enabled:false}")
    private boolean fcmEnabled;

    @Override
    public void sendToUser(User user, String title, String body) {
        if (!fcmEnabled) {
            log.warn("FCM is disabled. Skipping push notification for userId={}", user.getId());
            return;
        }

        List<UserDevice> devices = userDeviceRepository.findAllByUserIdAndIsActiveTrue(user.getId());

        if (devices.isEmpty()) {
            log.warn("No active devices found for userId={}", user.getId());
            return;
        }

        for (UserDevice device : devices) {
            if (device.getFcmToken() != null && !device.getFcmToken().isEmpty()) {
                sendToToken(device.getFcmToken(), title, body);
            }
        }
    }

    @Override
    public void sendToToken(String fcmToken, String title, String body) {
        if (!fcmEnabled) {
            log.warn("FCM is disabled. Skipping push notification");
            return;
        }

        if (fcmToken == null || fcmToken.isEmpty()) {
            log.warn("FCM token is null or empty. Cannot send notification.");
            return;
        }

        int attempt = 0;

        while (true) {
            try {
                Message message = Message.builder()
                        .setToken(fcmToken)
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .build();

                String response = FirebaseMessaging.getInstance().send(message);
                log.info("Push notification sent successfully: response={}", response);
                return;

            } catch (FirebaseMessagingException e) {
                attempt++;

                if (attempt > MAX_RETRIES) {
                    log.error("Push notification failed after {} retries: error={}", MAX_RETRIES, e.getMessage(), e);
                    return;
                }

                log.warn("Push notification failed (attempt {}/{}), retrying...", attempt, MAX_RETRIES);

                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
