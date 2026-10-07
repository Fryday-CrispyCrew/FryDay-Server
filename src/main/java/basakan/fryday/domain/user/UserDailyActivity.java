package basakan.fryday.domain.user;

import basakan.fryday.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 유저가 그날(Asia/Seoul) 인증된 API 요청을 한 번이라도 보냈다는 기록. DAU·리텐션 집계의 원천이다.
 * 탈퇴로 계정이 지워져도 남긴다. 지우면 리텐션이 실제보다 좋게 나온다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "user_daily_activity",
        indexes = {
                @Index(name = "idx_uda_activity_date", columnList = "activity_date")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_daily_activity", columnNames = {"user_id", "activity_date"})
        }
)
public class UserDailyActivity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;
}
