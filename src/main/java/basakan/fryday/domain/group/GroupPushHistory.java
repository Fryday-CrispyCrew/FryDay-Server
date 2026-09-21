package basakan.fryday.domain.group;

import basakan.fryday.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

/**
 * 하루 한 번만 보내는 그룹 알림의 발송 기록. unique 제약으로 같은 날 같은 알림이 두 번 나가지 않게 한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "group_push_history",
        indexes = {
                @Index(name = "idx_gph_user_date", columnList = "user_id, push_date")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_group_push_history",
                        columnNames = {"group_id", "user_id", "push_date", "push_type"})
        }
)
public class GroupPushHistory extends BaseEntity {

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "push_date", nullable = false)
    private LocalDate pushDate;

    // Hibernate 기본값인 MySQL enum 타입으로 만들면 알림 종류를 추가할 때마다 스키마를 바꿔야 한다
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "push_type", nullable = false, length = 30)
    private GroupPushType type;

    @Builder
    public GroupPushHistory(Long groupId, Long userId, LocalDate pushDate, GroupPushType type) {
        this.groupId = groupId;
        this.userId = userId;
        this.pushDate = pushDate;
        this.type = type;
    }
}
