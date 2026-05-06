package basakan.fryday.controller.todo.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Getter
@NoArgsConstructor
public class InstanceEditRequest {

    @NotNull
    private EditScope scope;

    @NotNull
    @Valid
    private Payload payload;

    public enum EditScope {
        THIS,
        THIS_AND_FUTURE,
        ALL
    }

    @Getter
    @NoArgsConstructor
    public static class Payload {
        private String title;
        private String memo;
        private Boolean isAlarmEnabled;
        private LocalTime alarmTime;
    }
}
