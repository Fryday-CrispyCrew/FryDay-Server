package basakan.fryday.api.dto;

import basakan.fryday.domain.Todo;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TodoSaveRequest {

    @NotBlank(message = "내용은 필수로 입력해야 합니다.")
    private String description;

    public TodoSaveRequest(String description) {
        this.description = description;
    }

    public Todo toEntity() {
        return Todo.builder()
                .description(description)
                .build();
    }
}
