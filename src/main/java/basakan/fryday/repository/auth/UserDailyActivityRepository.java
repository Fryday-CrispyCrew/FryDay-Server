package basakan.fryday.repository.auth;

import basakan.fryday.domain.user.UserDailyActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface UserDailyActivityRepository extends JpaRepository<UserDailyActivity, Long> {

    /**
     * 재시작이나 다중 인스턴스로 같은 날 두 번 들어와도 unique 키가 흡수하도록 조용히 무시한다.
     * native query 라 auditing 이 적용되지 않으므로 시각은 호출부에서 KST 로 넘긴다.
     */
    @Transactional
    @Modifying
    @Query(value = "INSERT IGNORE INTO user_daily_activity (user_id, activity_date, created_at, updated_at) " +
            "VALUES (:userId, :activityDate, :now, :now)", nativeQuery = true)
    void insertIgnore(@Param("userId") Long userId,
                      @Param("activityDate") LocalDate activityDate,
                      @Param("now") LocalDateTime now);
}
