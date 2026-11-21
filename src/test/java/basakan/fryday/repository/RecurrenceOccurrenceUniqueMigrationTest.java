package basakan.fryday.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 운영에서 확인한 중복 형태를 V8 스키마에 넣고 V9(반복 연결 해제), V10(유니크)을 적용한다.
 */
@Testcontainers
@DisplayName("반복 회차 중복 정리와 유니크 마이그레이션")
class RecurrenceOccurrenceUniqueMigrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("fryday_migration")
            .withUsername("root")
            .withPassword("test");

    private JdbcTemplate jdbc;

    @BeforeEach
    void migrateToV8AndSeed() {
        flyway(null).clean();
        flyway("8").migrate();

        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
                mysql.getJdbcUrl(), "root", mysql.getPassword(), true);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbc.execute("INSERT INTO category (id, color, display_order, name, user_id) VALUES (1, 'BR', 1, '운동', 100)");
        jdbc.batchUpdate(
                "INSERT INTO todo (id, date, deleted_at, description, display_order, recurrence_id, status, category_id, is_overridden) "
                        + "VALUES (?, ?, ?, '회차', 1, ?, ?, 1, ?)",
                List.of(
                        // 삭제된 행 + 살아있는 완료 행(알림) → 살아있는 행
                        row(1047, "2026-03-20", "2026-03-20", 18, "IN_PROGRESS", 0),
                        row(1055, "2026-03-20", null, 18, "COMPLETED", 0),
                        // 모두 삭제됨 → 가장 작은 id
                        row(1126, "2026-03-23", "2026-03-24", 7, "COMPLETED", 0),
                        row(1162, "2026-03-23", "2026-03-24", 7, "IN_PROGRESS", 0),
                        row(1381, "2026-03-23", "2026-03-25", 7, "COMPLETED", 0),
                        // 삭제된 행에 알림 + 살아있는 행 → 살아있는 행
                        row(1582, "2026-04-03", "2026-04-02", 36, "IN_PROGRESS", 0),
                        row(1585, "2026-04-03", null, 36, "IN_PROGRESS", 0),
                        // 살아있는 행이 둘 → 완료된 행. 나머지는 일반 투두로 화면에 남는다
                        row(2001, "2026-05-01", null, 40, "IN_PROGRESS", 0),
                        row(2002, "2026-05-01", null, 40, "COMPLETED", 0),
                        // 중복 없는 회차와 같은 날짜의 일반 투두는 그대로
                        row(3001, "2026-05-01", null, 41, "IN_PROGRESS", 0),
                        row(3002, "2026-05-01", null, null, "IN_PROGRESS", 0),
                        row(3003, "2026-05-01", null, null, "IN_PROGRESS", 0)
                ));
        jdbc.batchUpdate(
                "INSERT INTO todo_alarms (id, fail_count, notify_at, status, todo_id, user_id) "
                        + "VALUES (?, 0, '2026-03-20 09:00:00', 'SENT', ?, 100)",
                List.of(new Object[]{51, 1055}, new Object[]{60, 1582}));
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    @Test
    @DisplayName("규칙과 날짜마다 우선순위가 가장 높은 행만 연결을 남기고, 행은 지우지 않은 채 유니크 인덱스로 바꾼다")
    void detachesDuplicatesAndAddsUniqueIndex() {
        flyway(null).migrate();

        assertThat(todoSnapshot()).containsExactly(
                "1047|null|2026-03-20|IN_PROGRESS",
                "1055|18|null|COMPLETED",
                "1126|7|2026-03-24|COMPLETED",
                "1162|null|2026-03-24|IN_PROGRESS",
                "1381|null|2026-03-25|COMPLETED",
                "1582|null|2026-04-02|IN_PROGRESS",
                "1585|36|null|IN_PROGRESS",
                "2001|null|null|IN_PROGRESS",
                "2002|40|null|COMPLETED",
                "3001|41|null|IN_PROGRESS",
                "3002|null|null|IN_PROGRESS",
                "3003|null|null|IN_PROGRESS");
        assertThat(jdbc.queryForList("SELECT todo_id FROM todo_alarms ORDER BY todo_id", Long.class))
                .containsExactly(1055L, 1582L);

        assertThat(jdbc.queryForList(
                "SELECT CONCAT(index_name, '|', non_unique) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'todo' AND column_name = 'recurrence_id'",
                String.class))
                .containsExactly("uk_todo_recurrence_date|0");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO todo (date, description, display_order, recurrence_id, status, category_id, is_overridden) "
                        + "VALUES ('2026-03-23', '회차', 1, 7, 'IN_PROGRESS', 1, 0)"))
                .hasMessageContaining("Duplicate entry");
    }

    @Test
    @DisplayName("반복 연결 해제를 다시 실행해도 결과가 같다")
    void detachIsRepeatable() throws Exception {
        flyway(null).migrate();
        List<String> afterFirstRun = todoSnapshot();

        String script = new ClassPathResource("db/migration/V9__detach_duplicate_recurrence_occurrences.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        Arrays.stream(script.split(";"))
                .map(String::strip)
                .filter(sql -> !sql.lines().allMatch(line -> line.isBlank() || line.strip().startsWith("--")))
                .forEach(jdbc::execute);

        assertThat(todoSnapshot()).isEqualTo(afterFirstRun);
    }

    private List<String> todoSnapshot() {
        return jdbc.queryForList(
                "SELECT CONCAT_WS('|', id, IFNULL(recurrence_id, 'null'), IFNULL(deleted_at, 'null'), status) FROM todo ORDER BY id",
                String.class);
    }

    private static Object[] row(long id, String date, String deletedAt, Integer recurrenceId, String status, int overridden) {
        return new Object[]{id, date, deletedAt, recurrenceId, status, overridden};
    }

    /** target 이 null 이면 마지막 버전까지 적용한다. */
    private static Flyway flyway(String target) {
        var config = Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), "root", mysql.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false);
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }
}
