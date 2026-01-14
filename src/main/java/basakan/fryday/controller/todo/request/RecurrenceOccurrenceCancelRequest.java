package basakan.fryday.controller.todo.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class RecurrenceOccurrenceCancelRequest {

    @NotNull(message = "발생일은 필수입니다.")
    private LocalDate occurrenceDate;

    public RecurrenceOccurrenceCancelRequest(LocalDate occurrenceDate) {
        this.occurrenceDate = occurrenceDate;
    }
}