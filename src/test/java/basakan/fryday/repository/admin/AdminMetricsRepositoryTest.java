package basakan.fryday.repository.admin;

import basakan.fryday.domain.eventlog.EventLog;
import basakan.fryday.domain.eventlog.EventType;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.SocialAccountKey;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.admin.AdminMetricsRepository.Account;
import basakan.fryday.repository.admin.AdminMetricsRepository.SignUpEvent;
import basakan.fryday.repository.admin.AdminMetricsRepository.UserDate;
import basakan.fryday.repository.auth.UserDailyActivityRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:admin_metrics_repository_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        }
)
@Import(AdminMetricsRepository.class)
@DisplayName("어드민 지표 조회")
class AdminMetricsRepositoryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 20);

    @Autowired private AdminMetricsRepository adminMetricsRepository;
    @Autowired private UserDailyActivityRepository userDailyActivityRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("활동 기록은 기간 안의 날짜만 가져온다")
    void activeUsers() {
        // given
        userDailyActivityRepository.insertIgnore(1L, DAY.minusDays(1), LocalDateTime.now());
        userDailyActivityRepository.insertIgnore(1L, DAY, LocalDateTime.now());
        userDailyActivityRepository.insertIgnore(2L, DAY.plusDays(1), LocalDateTime.now());
        userDailyActivityRepository.insertIgnore(3L, DAY.plusDays(2), LocalDateTime.now());

        // when & then
        assertThat(adminMetricsRepository.findActiveUsers(DAY, DAY.plusDays(1)))
                .containsExactlyInAnyOrder(new UserDate(DAY, 1L), new UserDate(DAY.plusDays(1), 2L));
    }

    @Test
    @DisplayName("완료 유저는 완료 이벤트 시각의 날짜로 가져오고, 하루에 여러 번 완료해도 한 행이다")
    void completedUsers() {
        // given
        event(1L, EventType.TODO_COMPLETED, null, DAY.atStartOfDay());
        event(1L, EventType.TODO_COMPLETED, null, DAY.atTime(18, 0));
        event(1L, EventType.TODO_COMPLETED, null, DAY.plusDays(1).atTime(23, 59, 59));
        event(2L, EventType.TODO_UNCOMPLETED, null, DAY.atTime(12, 0));
        event(3L, EventType.TODO_COMPLETED, null, DAY.minusDays(1).atTime(23, 59, 59));
        event(4L, EventType.TODO_COMPLETED, null, DAY.plusDays(2).atStartOfDay());
        entityManager.flush();

        // when & then
        assertThat(adminMetricsRepository.findCompletedUsers(DAY, DAY.plusDays(1)))
                .containsExactlyInAnyOrder(new UserDate(DAY, 1L), new UserDate(DAY.plusDays(1), 1L));
    }

    @Test
    @DisplayName("계정은 소셜 계정 키, 가입 시각, 온보딩 완료 여부를 가져온다")
    void accounts() {
        // given
        Long onboarded = user("onboarded", DAY.atStartOfDay());
        Long pending = user("pending", DAY.atTime(9, 0));
        jdbcTemplate.update("UPDATE users SET onboarding_status = 'COMPLETED' WHERE id = ?", onboarded);

        // when & then
        assertThat(adminMetricsRepository.findAccounts()).containsExactlyInAnyOrder(
                new Account(onboarded, SocialAccountKey.of(AuthProvider.KAKAO, "onboarded"), DAY.atStartOfDay(), true),
                new Account(pending, SocialAccountKey.of(AuthProvider.KAKAO, "pending"), DAY.atTime(9, 0), false));
    }

    @Test
    @DisplayName("가입과 온보딩 완료 이벤트만 가져오고, 소셜 계정 키가 있으면 읽는다")
    void signUpEvents() {
        // given
        event(1L, EventType.USER_SIGNED_UP, null, DAY.atStartOfDay());
        event(2L, EventType.USER_SIGNED_UP, Map.of("provider", "KAKAO", "accountKey", "key-2"), DAY.atTime(1, 0));
        event(2L, EventType.ONBOARDING_COMPLETED, null, DAY.atTime(3, 0));
        event(2L, EventType.TODO_COMPLETED, null, DAY.atTime(4, 0));
        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThat(adminMetricsRepository.findSignUpEvents()).containsExactlyInAnyOrder(
                new SignUpEvent(1L, EventType.USER_SIGNED_UP, DAY.atStartOfDay(), null),
                new SignUpEvent(2L, EventType.USER_SIGNED_UP, DAY.atTime(1, 0), "key-2"),
                new SignUpEvent(2L, EventType.ONBOARDING_COMPLETED, DAY.atTime(3, 0), null));
    }

    @Test
    @DisplayName("활동 기록 수집 시작일은 가장 이른 활동 날짜이고, 기록이 없으면 비어 있다")
    void activityTrackedSince() {
        assertThat(adminMetricsRepository.findActivityTrackedSince()).isEmpty();

        // given
        userDailyActivityRepository.insertIgnore(1L, DAY, LocalDateTime.now());
        userDailyActivityRepository.insertIgnore(2L, DAY.minusDays(3), LocalDateTime.now());

        // when & then
        assertThat(adminMetricsRepository.findActivityTrackedSince()).contains(DAY.minusDays(3));
    }

    @Test
    @DisplayName("팀 계정 user_id 를 모두 가져온다")
    void internalUserIds() {
        // given
        jdbcTemplate.update("INSERT INTO internal_account (user_id, memo) VALUES (?, ?)", 7L, "팀원");
        jdbcTemplate.update("INSERT INTO internal_account (user_id, memo) VALUES (?, ?)", 8L, "팀원");

        // when & then
        assertThat(adminMetricsRepository.findInternalUserIds()).containsExactlyInAnyOrder(7L, 8L);
    }

    private Long user(String providerUserId, LocalDateTime createdAt) {
        User user = User.createNewUser(AuthProvider.KAKAO, providerUserId, providerUserId + "@test.com");
        entityManager.persist(user);
        entityManager.flush();
        jdbcTemplate.update("UPDATE users SET created_at = ? WHERE id = ?", createdAt, user.getId());
        return user.getId();
    }

    private void event(Long userId, EventType type, Map<String, Object> properties, LocalDateTime occurredAt) {
        entityManager.persist(EventLog.builder()
                .userId(userId)
                .eventType(type)
                .properties(properties)
                .occurredAt(occurredAt)
                .build());
    }
}
