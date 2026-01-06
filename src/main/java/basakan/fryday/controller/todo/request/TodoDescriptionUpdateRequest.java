package basakan.fryday.controller.todo.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TodoDescriptionUpdateRequest {

    @NotBlank(message = "할 일 내용은 필수입니다.")
    private String description;

    public TodoDescriptionUpdateRequest(String description) {
        this.description = description;
    }
}
