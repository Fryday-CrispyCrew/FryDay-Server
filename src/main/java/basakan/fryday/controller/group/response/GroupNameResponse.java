package basakan.fryday.controller.group.response;

import basakan.fryday.domain.group.FryGroup;
import lombok.Getter;

@Getter
public class GroupNameResponse {

    private final Long groupId;
    private final String name;

    private GroupNameResponse(FryGroup group) {
        this.groupId = group.getId();
        this.name = group.getName();
    }

    public static GroupNameResponse from(FryGroup group) {
        return new GroupNameResponse(group);
    }
}
