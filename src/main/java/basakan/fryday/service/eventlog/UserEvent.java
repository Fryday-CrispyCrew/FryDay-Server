package basakan.fryday.service.eventlog;

import basakan.fryday.domain.eventlog.EventType;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

public record UserEvent(Long userId, EventType type, Long targetId, Map<String, Object> properties,
                        LocalDateTime occurredAt) {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    public static UserEvent of(Long userId, EventType type) {
        return of(userId, type, null, null);
    }

    public static UserEvent of(Long userId, EventType type, Long targetId) {
        return of(userId, type, targetId, null);
    }

    // 저장은 커밋 뒤 다른 스레드에서 하므로, 발생 시각은 발행할 때 잡아둔다
    public static UserEvent of(Long userId, EventType type, Long targetId, Map<String, Object> properties) {
        return new UserEvent(userId, type, targetId, properties, LocalDateTime.now(KOREA_ZONE));
    }
}
