package basakan.fryday.repository.admin;

import basakan.fryday.domain.eventlog.EventLog;
import basakan.fryday.domain.eventlog.EventType;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.OnboardingStatus;
import basakan.fryday.domain.user.SocialAccountKey;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// 여러 테이블을 가로지르는 지표용 읽기 쿼리. 집계는 서비스에서 하고 여기서는 원본 행만 가져온다
@Repository
@RequiredArgsConstructor
public class AdminMetricsRepository {

    private static final RowMapper<UserDate> USER_DATE_MAPPER = (rs, rowNum) ->
            new UserDate(rs.getObject(1, LocalDate.class), rs.getLong(2));

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    public List<UserDate> findActiveUsers(LocalDate from, LocalDate to) {
        return jdbcTemplate.query("""
                SELECT activity_date, user_id
                FROM user_daily_activity
                WHERE activity_date BETWEEN :from AND :to
                """, period(from, to), USER_DATE_MAPPER);
    }

    // 그 날짜에 잡힌 투두를 하나라도 완료 상태로 둔 유저
    public List<UserDate> findCompletedUsers(LocalDate from, LocalDate to) {
        return jdbcTemplate.query("""
                SELECT DISTINCT t.date, c.user_id
                FROM todo t
                JOIN category c ON c.id = t.category_id
                WHERE t.status = 'COMPLETED'
                  AND t.deleted_at IS NULL
                  AND t.date BETWEEN :from AND :to
                """, period(from, to), USER_DATE_MAPPER);
    }

    // created_at 은 KST 로 저장된다 (컨테이너 TZ Asia/Seoul)
    public List<UserDate> findSignUps(LocalDate from, LocalDate to) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("fromStart", from.atStartOfDay())
                .addValue("toEndExclusive", to.plusDays(1).atStartOfDay());
        return jdbcTemplate.query("""
                SELECT CAST(created_at AS DATE), id
                FROM users
                WHERE created_at >= :fromStart AND created_at < :toEndExclusive
                """, params, USER_DATE_MAPPER);
    }

    // 완료 후 취소했어도 완료한 경험은 있으므로 센다
    public Set<Long> findTodoCompletedUsers(LocalDate from, LocalDate to) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("type", EventType.TODO_COMPLETED.name())
                .addValue("fromStart", from.atStartOfDay())
                .addValue("toEndExclusive", to.plusDays(1).atStartOfDay());
        return new HashSet<>(jdbcTemplate.queryForList("""
                SELECT DISTINCT user_id
                FROM event_log
                WHERE event_type = :type
                  AND occurred_at >= :fromStart AND occurred_at < :toEndExclusive
                """, params, Long.class));
    }

    // 지금 남아 있는 계정만 나온다. 탈퇴 후 지워진 계정은 가입 이벤트로 보완한다
    public List<Account> findAccounts() {
        return jdbcTemplate.query("""
                SELECT id, provider, provider_user_id, created_at, onboarding_status
                FROM users
                """, (rs, rowNum) -> new Account(
                rs.getLong(1),
                SocialAccountKey.of(AuthProvider.valueOf(rs.getString(2)), rs.getString(3)),
                rs.getObject(4, LocalDateTime.class),
                OnboardingStatus.COMPLETED.name().equals(rs.getString(5))));
    }

    // properties 를 DB 마다 다른 JSON 함수 없이 읽으려고 엔티티로 가져온다
    public List<SignUpEvent> findSignUpEvents() {
        return entityManager.createQuery("""
                        SELECT e FROM EventLog e
                        WHERE e.eventType IN :types
                        """, EventLog.class)
                .setParameter("types", List.of(EventType.USER_SIGNED_UP, EventType.ONBOARDING_COMPLETED))
                .getResultList().stream()
                .map(SignUpEvent::from)
                .toList();
    }

    public Optional<LocalDate> findActivityTrackedSince() {
        return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT MIN(activity_date) FROM user_daily_activity", new MapSqlParameterSource(), LocalDate.class));
    }

    public Set<Long> findInternalUserIds() {
        return new HashSet<>(jdbcTemplate.queryForList(
                "SELECT user_id FROM internal_account", new MapSqlParameterSource(), Long.class));
    }

    private MapSqlParameterSource period(LocalDate from, LocalDate to) {
        return new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to);
    }

    public record UserDate(LocalDate date, Long userId) {
    }

    public record Account(Long userId, String accountKey, LocalDateTime createdAt, boolean onboardingCompleted) {
    }

    // accountKey 는 이 키를 남기기 시작한 뒤의 가입 이벤트에만 있다
    public record SignUpEvent(Long userId, EventType type, LocalDateTime occurredAt, String accountKey) {

        static SignUpEvent from(EventLog eventLog) {
            Map<String, Object> properties = eventLog.getProperties();
            Object accountKey = properties == null ? null : properties.get("accountKey");
            return new SignUpEvent(eventLog.getUserId(), eventLog.getEventType(), eventLog.getOccurredAt(),
                    accountKey instanceof String key ? key : null);
        }
    }
}
