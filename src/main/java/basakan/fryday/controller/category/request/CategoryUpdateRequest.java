package basakan.fryday.controller.category.request;

import basakan.fryday.domain.todo.CategoryColor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CategoryUpdateRequest {

    @NotBlank(message = "카테고리 이름은 필수입니다.")
    @Size(max = 8, message = "카테고리 이름은 최대 8자까지 가능합니다.")
    private String name;

    private CategoryColor color;

    public CategoryUpdateRequest(String name, CategoryColor color) {
        this.name = name;
        this.color = color;
    }
}
