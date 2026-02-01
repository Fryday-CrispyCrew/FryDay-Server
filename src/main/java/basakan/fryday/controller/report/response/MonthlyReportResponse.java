package basakan.fryday.controller.report.response;

import basakan.fryday.domain.report.AttendanceIcon;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MonthlyReportResponse {
    private int year;
    private int month;
    private int totalTodos;
    private int completedTodos;
    private int incompleteTodos;
    private int attendanceDays;
    private double achievementRate;
    private AttendanceIcon attendanceIcon;
    private String attendanceMessage;
    private List<CategoryReportResponse> categories;

    public static MonthlyReportResponse ofRealtime(
            int year, int month,
            int totalTodos, int completedTodos, int incompleteTodos,
            int attendanceDays, double achievementRate,
            AttendanceIcon attendanceIcon,
            List<CategoryReportResponse> categories) {
        return MonthlyReportResponse.builder()
            .year(year)
            .month(month)
            .totalTodos(totalTodos)
            .completedTodos(completedTodos)
            .incompleteTodos(incompleteTodos)
            .attendanceDays(attendanceDays)
            .achievementRate(achievementRate)
            .attendanceIcon(attendanceIcon)
            .attendanceMessage(attendanceIcon.getMessage())
            .categories(categories)
            .build();
    }
}
