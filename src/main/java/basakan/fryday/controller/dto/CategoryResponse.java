package basakan.fryday.controller.dto;

import basakan.fryday.domain.Category;
import basakan.fryday.domain.CategoryColor;
import lombok.Getter;

@Getter
public class CategoryResponse {

    private final Long id;
    private final String name;
    private final CategoryColor color;

    public CategoryResponse(Category category) {
        this.id = category.getId();
        this.name = category.getName();
        this.color = category.getColor();
    }

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category);
    }
}
