package basakan.fryday.controller.todo.request;

import basakan.fryday.domain.todo.RecurrenceScope;
import basakan.fryday.domain.todo.RecurrenceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class InstanceEditRequest {

    @NotNull
    private RecurrenceScope scope;

    @NotNull
    @Valid
    private Payload payload;

    @Getter
    @NoArgsConstructor
    public static class Payload {
        // content 필드
        private String title;
        private String memo;
        private Boolean isAlarmEnabled;
        private LocalTime alarmTime;

        // rule 필드 (scope=THIS 에서는 무시됨)
        private RecurrenceType type;
        private List<String> frequencyValues;
        private LocalDate startDate;  // scope=ALL 에서만 사용
        private LocalDate endDate;
    }
}
