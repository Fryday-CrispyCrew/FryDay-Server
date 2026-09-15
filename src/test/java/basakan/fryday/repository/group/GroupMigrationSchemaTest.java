package basakan.fryday.repository.group;

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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영 DB는 ddl-auto: validate 라, 손으로 쓴 마이그레이션이 엔티티 매핑과 어긋나면 애플리케이션이 기동되지 않는다.
 * 같은 MySQL 인스턴스 안에서
 * ① Hibernate 가 엔티티 매핑으로 만든 그룹 테이블과
 * ② 마이그레이션 파일의 CREATE TABLE 로 만든 그룹 테이블을
 * information_schema 로 비교해 컬럼/인덱스가 동일한지 확인한다.
 * 마이그레이션 파일을 복사하지 않고 원본을 읽어 실행하므로 둘이 어긋날 수 없다.
 */
@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("그룹 테이블 마이그레이션 스크립트")
class GroupMigrationSchemaTest {

    private static final Path MIGRATION = Path.of("db/2026-09-15_create_group_tables.sql");
    private static final String HIBERNATE_SCHEMA = "fryday_group_schema";
    private static final String MIGRATION_SCHEMA = "fryday_migration_check";
    private static final List<String> GROUP_TABLES =
            List.of("fry_group", "group_member", "group_public_category");

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
    @DisplayName("마이그레이션 DDL 이 엔티티 매핑과 동일한 컬럼·인덱스를 만든다")
    void migrationMatchesEntityMapping() throws IOException, SQLException {
        try (Connection connection = dataSource.getConnection()) {
            applyMigrationToSeparateSchema(connection);

            for (String table : GROUP_TABLES) {
                assertThat(columnsOf(connection, MIGRATION_SCHEMA, table))
                        .as("%s 컬럼 정의", table)
                        .isEqualTo(columnsOf(connection, HIBERNATE_SCHEMA, table));

                assertThat(indexesOf(connection, MIGRATION_SCHEMA, table))
                        .as("%s 인덱스 정의", table)
                        .isEqualTo(indexesOf(connection, HIBERNATE_SCHEMA, table));
            }
        }
    }

    private void applyMigrationToSeparateSchema(Connection connection) throws IOException, SQLException {
        List<String> createTables = Arrays.stream(Files.readString(MIGRATION).split(";"))
                .map(GroupMigrationSchemaTest::stripComments)
                .filter(statement -> statement.toUpperCase().startsWith("CREATE TABLE"))
                .toList();
        assertThat(createTables).as("마이그레이션 파일의 CREATE TABLE 문").hasSize(GROUP_TABLES.size());

        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + MIGRATION_SCHEMA);
            statement.execute("CREATE DATABASE " + MIGRATION_SCHEMA);
            statement.execute("USE " + MIGRATION_SCHEMA);
            for (String createTable : createTables) {
                statement.execute(createTable);
            }
            statement.execute("USE " + HIBERNATE_SCHEMA);
        }
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

    private List<String> query(Connection connection, String sql, String schema, String table) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (var preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, schema);
            preparedStatement.setString(2, table);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(resultSet.getString(1));
                }
            }
        }
        assertThat(rows).as("%s.%s 가 존재해야 한다", schema, table).isNotEmpty();
        return rows;
    }

    private static String stripComments(String statement) {
        return statement.lines()
                .filter(line -> !line.strip().startsWith("--"))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("")
                .strip();
    }
}
