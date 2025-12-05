package basakan.fryday.controller.todo.request;

import basakan.fryday.domain.todo.Recurrence;
import basakan.fryday.domain.todo.RecurrenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class RecurrenceCreateRequest {

    @NotBlank(message = "내용은 필수입니다.")
    private String description;

    @NotNull
    private long categoryId;

    @NotNull(message = "반복 유형은 필수입니다.")
    private RecurrenceType type;

    private List<String> frequencyValues;

    @NotNull(message = "시작일은 필수입니다.")
    private LocalDate startDate;

    @NotNull(message = "종료일은 필수입니다.")
    private LocalDate endDate;

    private LocalTime notificationTime;

    public Recurrence toEntity(long userId) {
        String valuesString = (frequencyValues != null && !frequencyValues.isEmpty())
                ? String.join(",", frequencyValues)
                : null;

        return Recurrence.builder()
                .userId(userId)
                .categoryId(categoryId)
                .description(description)
                .type(type)
                .frequencyValues(valuesString)
                .startDate(startDate)
                .endDate(endDate)
                .notificationTime(notificationTime)
                .build();
    }

}
