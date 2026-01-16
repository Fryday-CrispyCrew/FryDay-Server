package basakan.fryday.controller.todo.request;

import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.todo.Todo;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class TodoSaveRequest {

    @NotBlank(message = "내용은 필수로 입력해야 합니다.")
    @Size(max = 20, message = "내용은 최대 20자까지 입력할 수 있습니다.")
    private String description;

    @NotNull
    private Long categoryId;

    @FutureOrPresent(message = "과거 날짜로는 투두를 생성할 수 없습니다.")
    private LocalDate date;

    public TodoSaveRequest(String description, Long categoryId, LocalDate date) {
        this.categoryId = categoryId;
        this.description = description;
        this.date = date;
    }

    public Todo toEntity(Category category) {
        return Todo.builder()
                .description(this.description)
                .category(category)
                .date(this.date)  // date가 null이면 Todo.builder에서 오늘 날짜로 설정됨
                .recurrenceId(null)
                .build();
    }
}
