package basakan.fryday.controller.group.response;

import basakan.fryday.domain.group.FryGroup;
import lombok.Getter;

/**
 * 초대 코드 입력 팝업에서 참여 전에 보여줄 그룹 정보.
 * 정원이 찼거나 이미 참여한 그룹도 오류 대신 플래그로 내려줘서, 앱이 참여 버튼만 막으면 되게 한다.
 */
@Getter
public class GroupInvitePreviewResponse {

    private final Long groupId;
    private final String name;
    private final int memberCount;
    private final int maxMemberCount;
    private final boolean full;
    private final boolean alreadyJoined;

    private GroupInvitePreviewResponse(FryGroup group, int memberCount, boolean alreadyJoined) {
        this.groupId = group.getId();
        this.name = group.getName();
        this.memberCount = memberCount;
        this.maxMemberCount = FryGroup.MAX_MEMBER_COUNT;
        this.full = memberCount >= FryGroup.MAX_MEMBER_COUNT;
        this.alreadyJoined = alreadyJoined;
    }

    public static GroupInvitePreviewResponse of(FryGroup group, int memberCount, boolean alreadyJoined) {
        return new GroupInvitePreviewResponse(group, memberCount, alreadyJoined);
    }
}
