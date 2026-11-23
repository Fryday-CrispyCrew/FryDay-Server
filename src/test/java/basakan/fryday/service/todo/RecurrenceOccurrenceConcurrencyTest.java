package basakan.fryday.service.todo;

import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.todo.EndType;
import basakan.fryday.domain.todo.Recurrence;
import basakan.fryday.domain.todo.RecurrenceType;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.auth.UserJpaRepository;
import basakan.fryday.repository.todo.RecurrenceRepository;
import basakan.fryday.service.user.UserReadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        JpaConfig.class,
        RecurrenceOccurrenceMaterializeService.class,
        RecurrenceOccurrenceWriter.class,
        RecurrenceOccurrenceCalculator.class,
        UserReadService.class
})
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("반복 회차 동시 생성")
class RecurrenceOccurrenceConcurrencyTest {

    private static final int THREADS = 8;
    private static final int DAYS = 20;

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("fryday_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.autoconfigure.exclude", () ->
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");
    }

    @Autowired private RecurrenceOccurrenceMaterializeService materializeService;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private RecurrenceRepository recurrenceRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("같은 날짜 목록 조회가 동시에 들어와도 회차는 날짜마다 1행만 생기고 실패 로그가 없다")
    void concurrentListQueries_createOneRowPerDate(CapturedOutput output) throws Exception {
        User user = userJpaRepository.save(User.createNewUser(AuthProvider.APPLE, "concurrency-sub", "c@t.com"));
        Category category = categoryRepository.save(
                Category.builder().name("운동").color(CategoryColor.BR).userId(user.getId()).displayOrder(1L).build());
        LocalDate start = LocalDate.now();
        Recurrence recurrence = recurrenceRepository.save(Recurrence.builder()
                .userId(user.getId())
                .categoryId(category.getId())
                .description("스트레칭")
                .type(RecurrenceType.DAILY)
                .startDate(start)
                .endType(EndType.NONE)
                .lastGeneratedDate(start)
                .build());

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            for (int day = 0; day < DAYS; day++) {
                LocalDate date = start.plusDays(day);
                CountDownLatch ready = new CountDownLatch(THREADS);
                CountDownLatch go = new CountDownLatch(1);
                List<Future<?>> futures = new ArrayList<>();
                for (int i = 0; i < THREADS; i++) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        go.await();
                        materializeService.materializeOccurrencesForDate(user.getId(), date, null);
                        return null;
                    }));
                }
                ready.await();
                go.countDown();
                for (Future<?> future : futures) {
                    future.get();
                }
            }
        } finally {
            executor.shutdownNow();
        }

        List<Integer> rowsPerDate = jdbcTemplate.queryForList(
                "SELECT COUNT(*) FROM todo WHERE recurrence_id = ? GROUP BY date ORDER BY date",
                Integer.class, recurrence.getId());

        assertThat(rowsPerDate).hasSize(DAYS).containsOnly(1);
        assertThat(output.getAll()).doesNotContain("반복 투두 생성 실패");
    }
}
