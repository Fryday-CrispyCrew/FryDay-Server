package basakan.fryday.repository.report;

import basakan.fryday.domain.report.MonthlyReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MonthlyReportRepository extends JpaRepository<MonthlyReport, Long> {

    @Query("SELECT mr FROM MonthlyReport mr " +
           "LEFT JOIN FETCH mr.categories " +
           "WHERE mr.userId = :userId AND mr.year = :year AND mr.month = :month")
    Optional<MonthlyReport> findByUserIdAndYearAndMonth(
        @Param("userId") Long userId,
        @Param("year") int year,
        @Param("month") int month
    );

    boolean existsByUserIdAndYearAndMonth(Long userId, int year, int month);
}
