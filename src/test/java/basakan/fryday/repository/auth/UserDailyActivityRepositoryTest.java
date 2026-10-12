package basakan.fryday.repository.auth;

import basakan.fryday.common.config.JpaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
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
                "spring.datasource.url=jdbc:h2:mem:user_daily_activity_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        }
)
@Import(JpaConfig.class)
@DisplayName("일별 활동 기록 저장소")
class UserDailyActivityRepositoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);

    @Autowired private UserDailyActivityRepository repository;

    @Test
    @DisplayName("같은 유저와 날짜로 두 번 기록해도 한 행만 남는다")
    void insertIgnoreKeepsOneRowPerDay() {
        // when
        repository.insertIgnore(1L, TODAY, LocalDateTime.now());
        repository.insertIgnore(1L, TODAY, LocalDateTime.now());
        repository.insertIgnore(1L, TODAY.plusDays(1), LocalDateTime.now());
        repository.insertIgnore(2L, TODAY, LocalDateTime.now());

        // then
        assertThat(repository.count()).isEqualTo(3);
    }
}
