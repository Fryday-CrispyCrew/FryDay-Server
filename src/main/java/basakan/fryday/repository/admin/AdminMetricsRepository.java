package basakan.fryday.repository.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 여러 테이블을 가로지르는 지표용 읽기 쿼리. 집계는 서비스에서 하고 여기서는 (날짜, 유저) 원본 행만 가져온다
@Repository
@RequiredArgsConstructor
public class AdminMetricsRepository {

    private static final RowMapper<UserDate> USER_DATE_MAPPER = (rs, rowNum) ->
            new UserDate(rs.getObject(1, LocalDate.class), rs.getLong(2));

    private final NamedParameterJdbcTemplate jdbcTemplate;

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
}
