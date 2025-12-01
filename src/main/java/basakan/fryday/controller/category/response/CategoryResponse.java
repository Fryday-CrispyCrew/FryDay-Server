package basakan.fryday.controller.category.response;

import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.todo.CategoryColor;
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
