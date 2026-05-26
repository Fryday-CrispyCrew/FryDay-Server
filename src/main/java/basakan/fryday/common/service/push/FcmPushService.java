package basakan.fryday.common.service.push;

import basakan.fryday.domain.user.User;
import basakan.fryday.domain.user.UserDevice;
import basakan.fryday.repository.auth.UserDeviceRepository;
import basakan.fryday.service.fcm.UserDeviceWriteService;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmPushService implements PushService {

    private final UserDeviceRepository userDeviceRepository;
    private final UserDeviceWriteService userDeviceWriteService;

    @Value("${fcm.enabled:false}")
    private boolean fcmEnabled;

    @Override
    @Transactional(readOnly = true)
    public void sendToUser(User user, String title, String body) {
        if (!fcmEnabled) {
            log.warn("FCM is disabled. Skipping push notification for userId={}", user.getId());
            return;
        }

        List<UserDevice> devices = userDeviceRepository.findAllByUserIdAndIsActiveTrueAndPushNotificationAgreedTrue(user.getId());

        if (devices.isEmpty()) {
            log.warn("No active devices found for userId={}", user.getId());
            return;
        }

        Set<String> sentTokens = new HashSet<>();
        for (UserDevice device : devices) {
            String token = device.getFcmToken();
            if (token != null && !token.isBlank() && sentTokens.add(token)) {
                sendToTokenInternal(device.getId(), token, device.getDeviceId(), title, body);
            }
        }
    }

    @Override
    public void sendToToken(String fcmToken, String title, String body) {
        if (!fcmEnabled) {
            log.warn("FCM is disabled. Skipping push notification");
            return;
        }

        if (fcmToken == null || fcmToken.isBlank()) {
            log.warn("FCM token is null or blank. Cannot send notification.");
            return;
        }

        try {
            Message message = buildMessage(fcmToken, title, body);

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Push notification sent successfully: response={}", response);

        } catch (FirebaseMessagingException e) {
            handleFcmException(e, fcmToken, null);
        }
    }

    private void sendToTokenInternal(Long deviceId, String fcmToken, String physicalDeviceId, String title, String body) {
        try {
            Message message = buildMessage(fcmToken, title, body);

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Push notification sent successfully: response={}, deviceId={}", response, physicalDeviceId);

        } catch (FirebaseMessagingException e) {
            handleFcmException(e, fcmToken, deviceId);
        } catch (RuntimeException e) {
            log.error("Unexpected error sending push notification: deviceId={}, error={}", physicalDeviceId, e.getMessage(), e);
        }
    }

    private Message buildMessage(String fcmToken, String title, String body) {
        return Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .setAndroidConfig(AndroidConfig.builder()
                        .setNotification(AndroidNotification.builder()
                                .setSound("default")
                                .setDefaultVibrateTimings(true)
                                .build())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder()
                                .setSound("default")
                                .build())
                        .build())
                .build();
    }

    private void handleFcmException(FirebaseMessagingException e, String fcmToken, Long deviceId) {
        String maskedToken = maskToken(fcmToken);
        MessagingErrorCode errorCode = e.getMessagingErrorCode();

        if (errorCode == null) {
            log.error("FCM error without error code: token={}, message={}", maskedToken, e.getMessage(), e);
            return;
        }

        switch (errorCode) {
            case UNREGISTERED:
                log.warn("FCM token unregistered, deactivating device: token={}", maskedToken);
                if (deviceId != null) {
                    userDeviceWriteService.deactivateDeviceById(deviceId);
                }
                break;

            case INVALID_ARGUMENT:
                log.warn("FCM invalid token format, deactivating device: token={}", maskedToken);
                if (deviceId != null) {
                    userDeviceWriteService.deactivateDeviceById(deviceId);
                }
                break;

            case SENDER_ID_MISMATCH:
                log.error("FCM sender ID mismatch - check Firebase configuration: token={}", maskedToken);
                break;

            case QUOTA_EXCEEDED:
                log.warn("FCM quota exceeded, will retry later: token={}", maskedToken);
                break;

            case UNAVAILABLE:
            case INTERNAL:
                log.warn("FCM temporary error ({}), will retry later: token={}", errorCode, maskedToken);
                break;

            case THIRD_PARTY_AUTH_ERROR:
                log.error("FCM third party auth error (APNs): token={}", maskedToken);
                break;

            default:
                log.error("FCM unknown error: code={}, token={}, message={}", errorCode, maskedToken, e.getMessage(), e);
                break;
        }
    }

    private static String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "[short-token]";
        }
        return "..." + token.substring(token.length() - 8);
    }
}
