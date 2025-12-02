package basakan.fryday.controller.category.request;

import basakan.fryday.domain.category.CategoryColor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CategoryCreateRequest {

    @NotBlank(message = "카테고리 이름은 필수입니다.")
    @Size(max = 8, message = "카테고리 이름은 최대 8자까지 가능합니다.")
    private String name;

    @NotNull(message = "카테고리 색상은 필수입니다.")
    private CategoryColor color;

    @NotNull
    private Long userId;

    public CategoryCreateRequest(String name, CategoryColor color, Long userId) {
        this.name = name;
        this.color = color;
        this.userId = userId;
    }
}
