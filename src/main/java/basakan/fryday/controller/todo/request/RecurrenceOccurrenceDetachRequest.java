package basakan.fryday.controller.todo.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class RecurrenceOccurrenceDetachRequest {

    @NotNull(message = "원본 발생일은 필수입니다.")
    private LocalDate occurrenceDate;

    @NotNull(message = "새 날짜는 필수입니다.")
    private LocalDate newDate;

    public RecurrenceOccurrenceDetachRequest(LocalDate occurrenceDate, LocalDate newDate) {
        this.occurrenceDate = occurrenceDate;
        this.newDate = newDate;
    }
}