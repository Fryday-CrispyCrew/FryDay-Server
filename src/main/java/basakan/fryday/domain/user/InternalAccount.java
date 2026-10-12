package basakan.fryday.domain.user;

import basakan.fryday.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "internal_account",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_internal_account_user", columnNames = {"user_id"})
        }
)
public class InternalAccount extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 누구 계정인지 정보 표시용
    @Column(nullable = false, length = 100)
    private String memo;
}
