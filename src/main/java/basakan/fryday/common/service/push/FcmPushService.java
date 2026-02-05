package basakan.fryday.common.service.push;

import basakan.fryday.domain.user.User;
import basakan.fryday.domain.user.UserDevice;
import basakan.fryday.repository.auth.UserDeviceRepository;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmPushService implements PushService {

    private final UserDeviceRepository userDeviceRepository;

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

        for (UserDevice device : devices) {
            if (device.getFcmToken() != null && !device.getFcmToken().isEmpty()) {
                sendToTokenInternal(device.getId(), device.getFcmToken(), device.getDeviceId(), title, body);
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

        try {
            Message message = buildMessage(fcmToken, title, body);

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Push notification sent successfully: response={}", response);

        } catch (FirebaseMessagingException e) {
            handleFcmException(e, fcmToken, null);
        }
    }

    private void sendToTokenInternal(Long deviceId, String fcmToken, String deviceIdStr, String title, String body) {
        try {
            Message message = buildMessage(fcmToken, title, body);

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Push notification sent successfully: response={}, deviceId={}", response, deviceIdStr);

        } catch (FirebaseMessagingException e) {
            handleFcmException(e, fcmToken, deviceId);
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
        MessagingErrorCode errorCode = e.getMessagingErrorCode();

        if (errorCode == null) {
            log.error("FCM error without error code: token={}, message={}", fcmToken, e.getMessage(), e);
            throw new RuntimeException("FCM push failed: " + e.getMessage(), e);
        }

        switch (errorCode) {
            case UNREGISTERED:
                log.warn("FCM token unregistered, deactivating device: token={}", fcmToken);
                if (deviceId != null) {
                    deactivateDevice(deviceId);
                }
                break;

            case INVALID_ARGUMENT:
                log.warn("FCM invalid token format, deactivating device: token={}", fcmToken);
                if (deviceId != null) {
                    deactivateDevice(deviceId);
                }
                break;

            case SENDER_ID_MISMATCH:
                log.error("FCM sender ID mismatch - check Firebase configuration: token={}", fcmToken);
                break;

            case QUOTA_EXCEEDED:
                log.warn("FCM quota exceeded, will retry later: token={}", fcmToken);
                throw new RuntimeException("FCM quota exceeded", e);

            case UNAVAILABLE:
            case INTERNAL:
                log.warn("FCM temporary error ({}), will retry later: token={}", errorCode, fcmToken);
                throw new RuntimeException("FCM temporary error: " + errorCode, e);

            case THIRD_PARTY_AUTH_ERROR:
                log.error("FCM third party auth error (APNs): token={}", fcmToken);
                break;

            default:
                log.error("FCM unknown error: code={}, token={}, message={}", errorCode, fcmToken, e.getMessage(), e);
                throw new RuntimeException("FCM push failed: " + errorCode, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deactivateDevice(Long deviceId) {
        userDeviceRepository.findById(deviceId)
                .ifPresent(device -> {
                    device.deactivate();
                    log.info("Device deactivated: deviceId={}", deviceId);
                });
    }
}
