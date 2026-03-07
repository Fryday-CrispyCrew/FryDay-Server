package basakan.fryday.service.report;

import basakan.fryday.controller.report.response.MonthlyReportResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Sql(scripts = "/sql/monthly-report-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MonthlyReportReadServiceIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("fryday_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
    }

    @Autowired
    private MonthlyReportReadService monthlyReportReadService;

    @Test
    @DisplayName("비동기 월간 리포트 조회 - 과거 월 데이터 정상 조회")
    void getMonthlyReportResponse_asyncPastMonth_success() {
        // given
        Long userId = 1L;
        int year = 2025;
        int month = 1;

        // when
        MonthlyReportResponse response = monthlyReportReadService.getMonthlyReportResponse(userId, year, month);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getYear()).isEqualTo(2025);
        assertThat(response.getMonth()).isEqualTo(1);
        assertThat(response.getTotalTodos()).isEqualTo(10);
        assertThat(response.getCompletedTodos()).isEqualTo(7);
        assertThat(response.getIncompleteTodos()).isEqualTo(3);
        assertThat(response.getAchievementRate()).isEqualTo(70.0);
        assertThat(response.getCategories()).hasSize(3);

        // 카테고리별 검증
        var categories = response.getCategories();

        // 운동: 3개 중 2개 완료
        var exercise = categories.stream()
                .filter(c -> c.getCategoryName().equals("운동"))
                .findFirst().orElseThrow();
        assertThat(exercise.getTotalTodos()).isEqualTo(3);
        assertThat(exercise.getCompletedTodos()).isEqualTo(2);

        // 공부: 3개 중 2개 완료
        var study = categories.stream()
                .filter(c -> c.getCategoryName().equals("공부"))
                .findFirst().orElseThrow();
        assertThat(study.getTotalTodos()).isEqualTo(3);
        assertThat(study.getCompletedTodos()).isEqualTo(2);

        // 독서: 4개 중 3개 완료
        var reading = categories.stream()
                .filter(c -> c.getCategoryName().equals("독서"))
                .findFirst().orElseThrow();
        assertThat(reading.getTotalTodos()).isEqualTo(4);
        assertThat(reading.getCompletedTodos()).isEqualTo(3);
    }

    @Test
    @DisplayName("비동기 처리로 3개 쿼리가 병렬 실행되어 결과 반환")
    void getMonthlyReportResponse_asyncExecution_returnsCorrectData() {
        // given
        Long userId = 1L;

        // when
        MonthlyReportResponse response = monthlyReportReadService.getMonthlyReportResponse(userId, 2025, 1);

        // then
        assertThat(response.getAttendanceDays()).isGreaterThan(0);
        assertThat(response.getAttendanceIcon()).isNotNull();
        assertThat(response.getCategories()).isNotEmpty();
    }
}
