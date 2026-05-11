package basakan.fryday.controller.todo.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CancelRecurrenceRequest {

    public enum CancelScope {
        THIS, THIS_AND_FUTURE, ALL
    }

    @NotNull
    private CancelScope scope;
}
