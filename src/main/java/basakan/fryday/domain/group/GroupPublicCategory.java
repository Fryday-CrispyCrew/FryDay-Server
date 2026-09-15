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
 * 사용자가 특정 그룹에 공개한 카테고리. 공개 설정은 그룹마다 독립적으로 관리한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "group_public_category",
        indexes = {
                // (group_id) 와 (group_id, user_id) 조회는 아래 unique 인덱스가 이미 커버한다.
                // 아래 둘은 회원 탈퇴 / 카테고리 삭제 시의 단일 컬럼 삭제를 위해 따로 필요하다.
                @Index(name = "idx_gpc_user", columnList = "user_id"),
                @Index(name = "idx_gpc_category", columnList = "category_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_group_public_category",
                        columnNames = {"group_id", "user_id", "category_id"})
        }
)
public class GroupPublicCategory extends BaseEntity {

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Builder
    public GroupPublicCategory(Long groupId, Long userId, Long categoryId) {
        this.groupId = groupId;
        this.userId = userId;
        this.categoryId = categoryId;
    }
}
