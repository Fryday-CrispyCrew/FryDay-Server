package basakan.fryday.repository;

import org.assertj.core.api.SoftAssertions;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영 DB는 ddl-auto: validate 이고, validate 는 인덱스를 검증하지 않는다.
 * 같은 MySQL 인스턴스 안에서
 * ① Hibernate 가 엔티티 매핑으로 만든 스키마와
 * ② 빈 스키마에 Flyway 마이그레이션을 처음부터 적용한 스키마를
 * information_schema 로 비교해, 모든 테이블의 컬럼과 인덱스가 같은지 확인한다.
 * 엔티티만 바꾸고 마이그레이션을 빠뜨리면 운영 기동이 아니라 여기서 실패한다.
 */
@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Flyway 마이그레이션 스키마")
class MigrationSchemaTest {

    private static final String HIBERNATE_SCHEMA = "fryday_entity_schema";
    private static final String MIGRATION_SCHEMA = "fryday_migration_schema";
    /** 엔티티는 없어졌지만 운영 DB에 남아 있는 테이블. 지울 때까지 비교에서 뺀다. */
    private static final List<String> LEGACY_TABLES = List.of("recurrence_exception");

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName(HIBERNATE_SCHEMA)
            .withUsername("root")
            .withPassword("test");

    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.autoconfigure.exclude", () ->
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");
    }

    @Test
    @DisplayName("빈 DB 에 마이그레이션을 모두 적용하면 엔티티 매핑과 같은 테이블·컬럼·인덱스가 만들어진다")
    void migrationsMatchEntityMapping() throws SQLException {
        Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), "root", mysql.getPassword())
                .schemas(MIGRATION_SCHEMA)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = dataSource.getConnection()) {
            List<String> entityTables = tablesOf(connection, HIBERNATE_SCHEMA);
            List<String> migrationTables = new ArrayList<>(tablesOf(connection, MIGRATION_SCHEMA));
            migrationTables.removeAll(LEGACY_TABLES);
            assertThat(migrationTables).as("테이블 목록").isEqualTo(entityTables);

            SoftAssertions softly = new SoftAssertions();
            for (String table : entityTables) {
                softly.assertThat(columnsOf(connection, MIGRATION_SCHEMA, table))
                        .as("%s 컬럼 정의", table)
                        .isEqualTo(columnsOf(connection, HIBERNATE_SCHEMA, table));

                softly.assertThat(indexesOf(connection, MIGRATION_SCHEMA, table))
                        .as("%s 인덱스 정의", table)
                        .isEqualTo(indexesOf(connection, HIBERNATE_SCHEMA, table));
            }
            softly.assertAll();
        }
    }

    private List<String> tablesOf(Connection connection, String schema) throws SQLException {
        return query(connection,
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = ? AND table_name <> 'flyway_schema_history' ORDER BY table_name",
                schema);
    }

    private List<String> columnsOf(Connection connection, String schema, String table) throws SQLException {
        return query(connection,
                "SELECT CONCAT_WS('|', column_name, column_type, is_nullable, IFNULL(column_default, '-')) "
                        + "FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name = ? ORDER BY column_name",
                schema, table);
    }

    private List<String> indexesOf(Connection connection, String schema, String table) throws SQLException {
        return query(connection,
                "SELECT CONCAT_WS('|', index_name, non_unique, GROUP_CONCAT(column_name ORDER BY seq_in_index)) "
                        + "FROM information_schema.statistics "
                        + "WHERE table_schema = ? AND table_name = ? "
                        + "GROUP BY index_name, non_unique ORDER BY index_name",
                schema, table);
    }

    private List<String> query(Connection connection, String sql, String... params) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (var preparedStatement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                preparedStatement.setString(i + 1, params[i]);
            }
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(resultSet.getString(1));
                }
            }
        }
        assertThat(rows).as("%s 조회 결과가 비어 있으면 안 된다", String.join(".", params)).isNotEmpty();
        return rows;
    }
}
