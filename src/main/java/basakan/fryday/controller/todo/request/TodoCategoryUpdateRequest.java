package basakan.fryday.controller.todo.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TodoCategoryUpdateRequest {

    @NotNull(message = "변경할 카테고리 ID는 필수입니다.")
    private Long categoryId;

    public TodoCategoryUpdateRequest(Long categoryId) {
        this.categoryId = categoryId;
    }
}

