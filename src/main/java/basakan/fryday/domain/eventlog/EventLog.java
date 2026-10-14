package basakan.fryday.domain.eventlog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "event_log",
        indexes = {
                @Index(name = "idx_event_log_type_occurred", columnList = "event_type, occurred_at"),
                @Index(name = "idx_event_log_user_occurred", columnList = "user_id, occurred_at")
        }
)
public class EventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // MySQL enum 으로 두면 이벤트 종류를 추가할 때마다 스키마를 바꿔야 한다
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    // 이벤트 대상의 id (투두, 반복, 그룹)
    @Column(name = "target_id")
    private Long targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> properties;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Builder
    private EventLog(Long userId, EventType eventType, Long targetId, Map<String, Object> properties,
                     LocalDateTime occurredAt) {
        this.userId = userId;
        this.eventType = eventType;
        this.targetId = targetId;
        this.properties = properties;
        this.occurredAt = occurredAt;
    }
}
