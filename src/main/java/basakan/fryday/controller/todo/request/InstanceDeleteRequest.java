package basakan.fryday.controller.todo.request;

import basakan.fryday.domain.todo.RecurrenceScope;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class InstanceDeleteRequest {

    @NotNull
    private RecurrenceScope scope;
}
