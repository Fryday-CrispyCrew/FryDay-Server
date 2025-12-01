package basakan.fryday.controller.todo.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class TodoDateUpdateRequest {

    @NotNull(message = "변경할 날짜는 필수입니다.")
    @FutureOrPresent(message = "과거 날짜로는 변경할 수 없습니다.")
    private LocalDate date;

    public TodoDateUpdateRequest(LocalDate date) {
        this.date = date;
    }
}
