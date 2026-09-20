package basakan.fryday.controller.group.response;

import basakan.fryday.domain.group.FryGroup;
import lombok.Getter;

@Getter
public class GroupJoinResponse {

    private final Long groupId;
    private final String name;
    private final int memberCount;
    private final int maxMemberCount;

    private GroupJoinResponse(FryGroup group, int memberCount) {
        this.groupId = group.getId();
        this.name = group.getName();
        this.memberCount = memberCount;
        this.maxMemberCount = FryGroup.MAX_MEMBER_COUNT;
    }

    public static GroupJoinResponse of(FryGroup group, int memberCount) {
        return new GroupJoinResponse(group, memberCount);
    }
}
