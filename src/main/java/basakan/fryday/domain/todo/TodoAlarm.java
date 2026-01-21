package basakan.fryday.domain.todo;

import basakan.fryday.domain.BaseEntity;
import basakan.fryday.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "todo_alarms")
public class TodoAlarm extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "todo_id", nullable = false)
    private Todo todo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime notifyAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlarmStatus status;

    @Column(nullable = false)
    private int failCount = 0;

    @Version
    private Long version;

    private static final int MAX_RETRY_COUNT = 3;

    public enum AlarmStatus {
        PENDING,  // 발송 대기
        SENT,     // 발송 완료
        FAILED    // 발송 실패 (최대 재시도 초과)
    }

    @Builder
    private TodoAlarm(Todo todo, User user, LocalDateTime notifyAt) {
        this.todo = todo;
        this.user = user;
        this.notifyAt = notifyAt;
        this.status = AlarmStatus.PENDING;
    }

    public static TodoAlarm create(Todo todo, User user, LocalDateTime notifyAt) {
        return TodoAlarm.builder()
                .todo(todo)
                .user(user)
                .notifyAt(notifyAt)
                .build();
    }

    public void changeTime(LocalDateTime notifyAt) {
        this.notifyAt = notifyAt;
        this.status = AlarmStatus.PENDING;
        this.failCount = 0;
    }

    public void markAsSent() {
        this.status = AlarmStatus.SENT;
    }

    public boolean incrementFailCount() {
        this.failCount++;
        if (this.failCount >= MAX_RETRY_COUNT) {
            this.status = AlarmStatus.FAILED;
            return true;
        }
        return false;
    }

    public boolean canRetry() {
        return this.status == AlarmStatus.PENDING && this.failCount < MAX_RETRY_COUNT;
    }
}
