package basakan.fryday.repository.admin;

import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.User;
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
    @DisplayName("완료 유저는 완료 상태이고 삭제되지 않은 투두만 보고, 하루에 여러 개 완료해도 한 행이다")
    void completedUsers() {
        // given
        Category mine = category(10L);
        Category other = category(20L);
        todo(mine, DAY, true, false);
        todo(mine, DAY, true, false);
        todo(mine, DAY.plusDays(1), false, false); // 미완료
        todo(other, DAY, true, true);              // 삭제됨
        todo(other, DAY.plusDays(1), true, false);
        todo(other, DAY.plusDays(5), true, false); // 기간 밖
        entityManager.flush();

        // when & then
        assertThat(adminMetricsRepository.findCompletedUsers(DAY, DAY.plusDays(1)))
                .containsExactlyInAnyOrder(new UserDate(DAY, 10L), new UserDate(DAY.plusDays(1), 20L));
    }

    @Test
    @DisplayName("가입은 가입 시각의 날짜로 가져오고, 종료일 자정 이후 가입은 빠진다")
    void signUps() {
        // given
        Long first = user("first", DAY.atStartOfDay());
        Long last = user("last", DAY.plusDays(1).atTime(23, 59, 59));
        user("before", DAY.minusDays(1).atTime(23, 59, 59));
        user("after", DAY.plusDays(2).atStartOfDay());

        // when & then
        assertThat(adminMetricsRepository.findSignUps(DAY, DAY.plusDays(1)))
                .containsExactlyInAnyOrder(new UserDate(DAY, first), new UserDate(DAY.plusDays(1), last));
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

    private Category category(Long userId) {
        Category category = new Category("공부", CategoryColor.OR, userId, 1L);
        entityManager.persist(category);
        return category;
    }

    private void todo(Category category, LocalDate date, boolean completed, boolean deleted) {
        Todo todo = new Todo("투두", category, date, 1L, null, null);
        if (completed) {
            todo.toggleCompletion();
        }
        if (deleted) {
            todo.delete();
        }
        entityManager.persist(todo);
    }

    private Long user(String providerUserId, LocalDateTime createdAt) {
        User user = User.createNewUser(AuthProvider.KAKAO, providerUserId, providerUserId + "@test.com");
        entityManager.persist(user);
        entityManager.flush();
        jdbcTemplate.update("UPDATE users SET created_at = ? WHERE id = ?", createdAt, user.getId());
        return user.getId();
    }
}
