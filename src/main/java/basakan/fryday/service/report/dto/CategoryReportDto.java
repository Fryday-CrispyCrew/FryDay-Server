package basakan.fryday.service.report.dto;

import basakan.fryday.domain.category.CategoryColor;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CategoryReportDto {
    private String categoryName;
    private CategoryColor categoryColor;
    private int totalTodos;
    private int completedTodos;
    private int incompleteTodos;
}
