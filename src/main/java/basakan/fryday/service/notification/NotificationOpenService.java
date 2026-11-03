package basakan.fryday.service.notification;

import basakan.fryday.controller.notification.request.NotificationOpenRequest;
import basakan.fryday.domain.eventlog.EventType;
import basakan.fryday.service.eventlog.UserEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationOpenService {

    private final ApplicationEventPublisher eventPublisher;

    // 해체된 그룹의 알림도 열 수 있으므로 그룹이나 그룹원인지는 확인하지 않는다
    public void recordOpen(NotificationOpenRequest request, Long userId) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("trigger", request.getTrigger());
        if (request.getType() != null) {
            properties.put("type", request.getType());
        }
        if (request.getPushId() != null) {
            // 서버는 소문자로 발급하므로, 대문자로 바꿔 보내도 발송 기록과 이어지게 맞춘다
            properties.put("pushId", request.getPushId().toLowerCase(Locale.ROOT));
        }

        eventPublisher.publishEvent(UserEvent.of(userId, EventType.NOTIFICATION_OPENED, request.getGroupId(), properties));
    }
}
