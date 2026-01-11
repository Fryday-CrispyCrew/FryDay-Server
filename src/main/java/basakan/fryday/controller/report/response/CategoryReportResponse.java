package basakan.fryday.controller.report.response;

import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.report.MonthlyReportCategory;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CategoryReportResponse {
    private Long categoryId;
    private String categoryName;
    private CategoryColor categoryColor;
    private int totalTodos;
    private int completedTodos;
    private int incompleteTodos;
    private double successRate;
    private double failureRate;

    public static CategoryReportResponse from(MonthlyReportCategory category) {
        return CategoryReportResponse.builder()
            .categoryId(category.getCategoryId())
            .categoryName(category.getCategoryName())
            .categoryColor(category.getCategoryColor())
            .totalTodos(category.getTotalTodos())
            .completedTodos(category.getCompletedTodos())
            .incompleteTodos(category.getIncompleteTodos())
            .successRate(category.getSuccessRate())
            .failureRate(category.getFailureRate())
            .build();
    }
}
