package basakan.fryday.controller.group.request;

import basakan.fryday.domain.group.GroupInteractionType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GroupInteractionRequest {

    @NotNull(message = "상호작용 종류는 필수입니다.")
    private GroupInteractionType type;

    public GroupInteractionRequest(GroupInteractionType type) {
        this.type = type;
    }
}
