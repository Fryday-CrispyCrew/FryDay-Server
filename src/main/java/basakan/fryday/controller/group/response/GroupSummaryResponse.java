package basakan.fryday.controller.group.response;

import basakan.fryday.domain.group.FryGroup;
import basakan.fryday.domain.group.GroupRole;
import basakan.fryday.service.group.dto.GroupSummaryDto;
import lombok.Getter;

@Getter
public class GroupSummaryResponse {

    private final Long groupId;
    private final String name;
    private final int memberCount;
    private final int maxMemberCount;
    private final GroupRole myRole;

    private GroupSummaryResponse(GroupSummaryDto group, Long userId) {
        this.groupId = group.getGroupId();
        this.name = group.getName();
        this.memberCount = (int) group.getMemberCount();
        this.maxMemberCount = FryGroup.MAX_MEMBER_COUNT;
        this.myRole = group.getOwnerId().equals(userId) ? GroupRole.OWNER : GroupRole.MEMBER;
    }

    public static GroupSummaryResponse of(GroupSummaryDto group, Long userId) {
        return new GroupSummaryResponse(group, userId);
    }
}
