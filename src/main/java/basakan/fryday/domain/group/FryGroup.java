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
 * 그룹. {@code group}/{@code groups}가 MySQL 예약어라 테이블·엔티티 모두 fry_group/FryGroup 을 쓴다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "fry_group",
        indexes = {
                @Index(name = "idx_fry_group_owner", columnList = "owner_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_fry_group_invite_code", columnNames = "invite_code")
        }
)
public class FryGroup extends BaseEntity {

    public static final int MAX_MEMBER_COUNT = 10;
    public static final int MAX_NAME_LENGTH = 10;
    public static final int INVITE_CODE_LENGTH = 6;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(name = "invite_code", nullable = false, length = INVITE_CODE_LENGTH)
    private String inviteCode;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Builder
    public FryGroup(String name, String inviteCode, Long ownerId) {
        this.name = name;
        this.inviteCode = inviteCode;
        this.ownerId = ownerId;
    }

    public void updateName(String name) {
        this.name = name;
    }

    public boolean isOwner(Long userId) {
        return this.ownerId.equals(userId);
    }
}
