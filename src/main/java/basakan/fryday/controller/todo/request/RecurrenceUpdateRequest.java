package basakan.fryday.controller.todo.request;

import basakan.fryday.domain.todo.RecurrenceType;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class RecurrenceUpdateRequest {

    @NotNull(message = "반복 유형은 필수입니다.")
    private RecurrenceType type;

    private List<String> frequencyValues;

    @NotNull(message = "시작일은 필수입니다.")
    private LocalDate startDate;

    private LocalDate endDate;

    private LocalTime notificationTime;
}