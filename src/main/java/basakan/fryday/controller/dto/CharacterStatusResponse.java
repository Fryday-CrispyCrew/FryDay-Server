package basakan.fryday.controller.dto;

import basakan.fryday.domain.CharacterStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CharacterStatusResponse {
    private CharacterStatus status;
    private String imageCode;
    private String description;
}
