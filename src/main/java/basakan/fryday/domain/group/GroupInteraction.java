package basakan.fryday.domain.group;

import basakan.fryday.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "group_interaction",
        indexes = {
                @Index(name = "idx_group_interaction_created", columnList = "created_at")
        }
)
public class GroupInteraction extends BaseEntity {

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    // Hibernate 기본값인 MySQL enum 타입으로 만들면 상호작용 종류를 추가할 때마다 스키마를 바꿔야 한다
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "interaction_type", nullable = false, length = 30)
    private GroupInteractionType type;

    @Builder
    public GroupInteraction(Long groupId, Long senderId, Long targetId, GroupInteractionType type) {
        this.groupId = groupId;
        this.senderId = senderId;
        this.targetId = targetId;
        this.type = type;
    }
}
