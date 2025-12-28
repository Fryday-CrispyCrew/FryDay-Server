package basakan.fryday.controller.report.response;

import basakan.fryday.domain.report.AttendanceIcon;
import basakan.fryday.domain.report.MonthlyReport;
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
    private double achievementRate;
    private AttendanceIcon attendanceIcon;
    private String attendanceMessage;
    private List<CategoryReportResponse> categories;

    public static MonthlyReportResponse from(MonthlyReport report) {
        return MonthlyReportResponse.builder()
            .year(report.getYear())
            .month(report.getMonth())
            .totalTodos(report.getTotalTodos())
            .completedTodos(report.getCompletedTodos())
            .incompleteTodos(report.getIncompleteTodos())
            .achievementRate(report.getAchievementRate())
            .attendanceIcon(report.getAttendanceIcon())
            .attendanceMessage(report.getAttendanceMessage())
            .categories(report.getCategories().stream()
                .map(CategoryReportResponse::from)
                .toList())
            .build();
    }
}
