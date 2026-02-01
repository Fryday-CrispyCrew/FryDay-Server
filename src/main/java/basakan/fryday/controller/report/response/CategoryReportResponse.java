package basakan.fryday.controller.report.response;

import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.service.report.dto.CategoryReportDto;
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

    public static CategoryReportResponse from(CategoryReportDto dto, double successRate, double failureRate) {
        return CategoryReportResponse.builder()
            .categoryId(dto.getCategoryId())
            .categoryName(dto.getCategoryName())
            .categoryColor(dto.getCategoryColor())
            .totalTodos(dto.getTotalTodos())
            .completedTodos(dto.getCompletedTodos())
            .incompleteTodos(dto.getIncompleteTodos())
            .successRate(successRate)
            .failureRate(failureRate)
            .build();
    }
}
