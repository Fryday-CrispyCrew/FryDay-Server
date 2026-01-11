package basakan.fryday.service.report;

import basakan.fryday.domain.report.AttendanceIcon;
import basakan.fryday.domain.report.MonthlyReport;
import basakan.fryday.domain.report.MonthlyReportCategory;
import basakan.fryday.repository.report.MonthlyReportRepository;
import basakan.fryday.repository.todo.TodoRepository;
import basakan.fryday.service.report.dto.CategoryReportDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MonthlyReportGenerateService {

    private static final double PERCENTAGE_SCALE = 100.0;
    private static final double ROUNDING_SCALE = 10000.0;

    private final MonthlyReportRepository monthlyReportRepository;
    private final TodoRepository todoRepository;

    public void generateMonthlyReport(Long userId, int year, int month) {
        if (monthlyReportRepository.existsByUserIdAndYearAndMonth(userId, year, month)) {
            return;
        }

        List<CategoryReportDto> categoryStatistics =
            todoRepository.findMonthlyReportByCategory(userId, year, month);

        int totalTodos = categoryStatistics.stream()
            .mapToInt(CategoryReportDto::getTotalTodos).sum();
        int completedTodos = categoryStatistics.stream()
            .mapToInt(CategoryReportDto::getCompletedTodos).sum();
        int incompleteTodos = totalTodos - completedTodos;
        int attendanceDays = todoRepository.countAttendanceDays(userId, year, month);
        double achievementRate = calculateRate(completedTodos, totalTodos);

        AttendanceIcon icon = AttendanceIcon.fromAttendanceDays(attendanceDays);

        MonthlyReport report = MonthlyReport.builder()
            .userId(userId)
            .year(year)
            .month(month)
            .totalTodos(totalTodos)
            .completedTodos(completedTodos)
            .incompleteTodos(incompleteTodos)
            .attendanceDays(attendanceDays)
            .achievementRate(achievementRate)
            .attendanceIcon(icon)
            .attendanceMessage(icon.getMessage())
            .build();

        for (CategoryReportDto dto : categoryStatistics) {
            double successRate = calculateRate(dto.getCompletedTodos(), dto.getTotalTodos());
            double failureRate = calculateRate(dto.getIncompleteTodos(), dto.getTotalTodos());

            MonthlyReportCategory categoryReport = MonthlyReportCategory.builder()
                .categoryId(dto.getCategoryId())
                .categoryName(dto.getCategoryName())
                .categoryColor(dto.getCategoryColor())
                .totalTodos(dto.getTotalTodos())
                .completedTodos(dto.getCompletedTodos())
                .incompleteTodos(dto.getIncompleteTodos())
                .successRate(successRate)
                .failureRate(failureRate)
                .build();

            report.addCategory(categoryReport);
        }

        monthlyReportRepository.save(report);
    }

    private double calculateRate(int numerator, int denominator) {
        if (denominator == 0) {
            return 0.0;
        }
        return Math.round(numerator * ROUNDING_SCALE / denominator) / PERCENTAGE_SCALE;
    }
}
