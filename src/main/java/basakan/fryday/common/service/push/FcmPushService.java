package basakan.fryday.common.service.push;

import basakan.fryday.domain.eventlog.EventType;
import basakan.fryday.domain.user.User;
import basakan.fryday.domain.user.UserDevice;
import basakan.fryday.repository.auth.UserDeviceRepository;
import basakan.fryday.service.eventlog.UserEvent;
import basakan.fryday.service.fcm.UserDeviceWriteService;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FcmOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmPushService implements PushService {

    // FCM 이 허용하는 analytics label 형식. 어기면 발송 자체가 실패하므로 맞는 경우에만 붙인다
    private static final Pattern ANALYTICS_LABEL_PATTERN = Pattern.compile(PUSH_TYPE_REGEX);

    private final UserDeviceRepository userDeviceRepository;
    private final UserDeviceWriteService userDeviceWriteService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${fcm.enabled:false}")
    private boolean fcmEnabled;

    @Override
    @Transactional(readOnly = true)
    public void sendToUser(User user, String title, String body, Map<String, String> data) {
        if (!fcmEnabled) {
            log.warn("FCM is disabled. Skipping push notification for userId={}", user.getId());
            return;
        }

        List<UserDevice> devices = userDeviceRepository.findAllByUserIdAndIsActiveTrueAndPushNotificationAgreedTrue(user.getId());

        if (devices.isEmpty()) {
            log.warn("No active devices found for userId={}", user.getId());
            return;
        }

        String pushId = UUID.randomUUID().toString();
        Map<String, String> pushData = new HashMap<>(data);
        pushData.put(PUSH_ID_KEY, pushId);

        Set<String> sentTokens = new HashSet<>();
        boolean sent = false;
        for (UserDevice device : devices) {
            String token = device.getFcmToken();
            if (token != null && !token.isBlank() && sentTokens.add(token)) {
                sent |= sendToTokenInternal(device.getId(), token, device.getDeviceId(), title, body, pushData);
            }
        }

        if (sent) {
            eventPublisher.publishEvent(UserEvent.of(user.getId(), EventType.PUSH_SENT, targetId(data),
                    pushSentProperties(data, pushId)));
        }
    }

    @Override
    public void sendToToken(String fcmToken, String title, String body, Map<String, String> data) {
        if (!fcmEnabled) {
            log.warn("FCM is disabled. Skipping push notification");
            return;
        }

        if (fcmToken == null || fcmToken.isBlank()) {
            log.warn("FCM token is null or blank. Cannot send notification.");
            return;
        }

        try {
            Message message = buildMessage(fcmToken, title, body, data);

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Push notification sent successfully: response={}", response);

        } catch (FirebaseMessagingException e) {
            handleFcmException(e, fcmToken, null);
        }
    }

    private boolean sendToTokenInternal(Long deviceId, String fcmToken, String physicalDeviceId, String title,
                                        String body, Map<String, String> data) {
        try {
            Message message = buildMessage(fcmToken, title, body, data);

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Push notification sent successfully: response={}, deviceId={}", response, physicalDeviceId);
            return true;

        } catch (FirebaseMessagingException e) {
            handleFcmException(e, fcmToken, deviceId);
        } catch (RuntimeException e) {
            log.error("Unexpected error sending push notification: deviceId={}, error={}", physicalDeviceId, e.getMessage(), e);
        }
        return false;
    }

    // 푸시는 이미 나간 뒤라, 여기서 예외가 나면 호출한 쪽이 발송 실패로 보고 다시 보낼 수 있다
    private static Long targetId(Map<String, String> data) {
        String id = data.getOrDefault(TODO_ID_KEY, data.get(GROUP_ID_KEY));
        if (id == null) {
            return null;
        }
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException e) {
            log.warn("발송 기록의 대상 ID 가 숫자가 아닙니다: {}", id);
            return null;
        }
    }

    private static Map<String, Object> pushSentProperties(Map<String, String> data, String pushId) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("pushId", pushId);
        if (data.containsKey(TYPE_KEY)) {
            properties.put("type", data.get(TYPE_KEY));
        }
        return properties;
    }

    private Message buildMessage(String fcmToken, String title, String body, Map<String, String> data) {
        Message.Builder builder = Message.builder();
        analyticsLabel(data).ifPresent(label -> builder.setFcmOptions(FcmOptions.withAnalyticsLabel(label)));

        return builder
                .setToken(fcmToken)
                .putAllData(data)
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

    // data 의 알림 종류(type)를 analytics label 로 써서, Firebase 콘솔에서 종류별 수신과 오픈을 볼 수 있게 한다
    static Optional<String> analyticsLabel(Map<String, String> data) {
        return Optional.ofNullable(data.get(TYPE_KEY))
                .filter(type -> ANALYTICS_LABEL_PATTERN.matcher(type).matches());
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
