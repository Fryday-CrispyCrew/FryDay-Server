package basakan.fryday.controller.dto;

import basakan.fryday.domain.Category;
import basakan.fryday.domain.Todo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TodoSaveRequest {

    @NotBlank(message = "내용은 필수로 입력해야 합니다.")
    private String description;

    @NotNull
    private Long categoryId;

    public TodoSaveRequest(String description, Long categoryId) {
        this.categoryId = categoryId;
        this.description = description;
    }

    public Todo toEntity(Category category) {
        return Todo.builder()
                .description(this.description)
                .category(category)
                .build();
    }
}
