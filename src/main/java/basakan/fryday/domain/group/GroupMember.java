package basakan.fryday.domain.group;

import basakan.fryday.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 그룹 참여 정보. 그룹장 여부는 {@link FryGroup#getOwnerId()}가 단일 소스이므로 role 컬럼을 두지 않는다.
 * 참여 순서는 {@code createdAt} 오름차순을 사용한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "group_member",
        indexes = {
                @Index(name = "idx_group_member_user", columnList = "user_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_group_member", columnNames = {"group_id", "user_id"})
        }
)
public class GroupMember extends BaseEntity {

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "notification_enabled", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 1")
    private boolean notificationEnabled = true;

    @Builder
    public GroupMember(Long groupId, Long userId) {
        this.groupId = groupId;
        this.userId = userId;
        this.notificationEnabled = true;
    }

    public void updateNotificationEnabled(boolean notificationEnabled) {
        this.notificationEnabled = notificationEnabled;
    }
}
