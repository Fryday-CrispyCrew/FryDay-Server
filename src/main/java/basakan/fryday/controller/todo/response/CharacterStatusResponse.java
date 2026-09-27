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

    public static CharacterStatusResponse from(CharacterStatus status) {
        return CharacterStatusResponse.builder()
                .status(status)
                .imageCode(status.getImageCode())
                .description(status.getDescription())
                .build();
    }
}
