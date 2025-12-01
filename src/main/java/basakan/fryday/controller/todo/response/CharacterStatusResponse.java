package basakan.fryday.controller.todo.response;

import basakan.fryday.domain.todo.CharacterStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CharacterStatusResponse {
    private CharacterStatus status;
    private String imageCode;
    private String description;
}
