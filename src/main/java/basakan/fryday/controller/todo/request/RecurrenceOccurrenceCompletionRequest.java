package basakan.fryday.controller.todo.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class RecurrenceOccurrenceCompletionRequest {

    @NotNull(message = "발생일은 필수입니다.")
    private LocalDate occurrenceDate;

    public RecurrenceOccurrenceCompletionRequest(LocalDate occurrenceDate) {
        this.occurrenceDate = occurrenceDate;
    }
}