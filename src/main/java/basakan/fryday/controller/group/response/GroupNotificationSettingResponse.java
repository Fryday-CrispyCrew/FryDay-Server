package basakan.fryday.controller.group.response;

import basakan.fryday.domain.group.GroupMember;
import lombok.Getter;

@Getter
public class GroupNotificationSettingResponse {

    private final Long groupId;
    private final boolean enabled;

    private GroupNotificationSettingResponse(GroupMember member) {
        this.groupId = member.getGroupId();
        this.enabled = member.isNotificationEnabled();
    }

    public static GroupNotificationSettingResponse from(GroupMember member) {
        return new GroupNotificationSettingResponse(member);
    }
}
