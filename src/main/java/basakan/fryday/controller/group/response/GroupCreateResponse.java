package basakan.fryday.controller.group.response;

import basakan.fryday.domain.group.FryGroup;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class GroupCreateResponse {

    private final Long groupId;
    private final String name;
    private final String inviteCode;
    private final int memberCount;
    private final int maxMemberCount;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime createdAt;

    private GroupCreateResponse(FryGroup group) {
        this.groupId = group.getId();
        this.name = group.getName();
        this.inviteCode = group.getInviteCode();
        this.memberCount = 1;
        this.maxMemberCount = FryGroup.MAX_MEMBER_COUNT;
        this.createdAt = group.getCreatedAt();
    }

    public static GroupCreateResponse from(FryGroup group) {
        return new GroupCreateResponse(group);
    }
}
