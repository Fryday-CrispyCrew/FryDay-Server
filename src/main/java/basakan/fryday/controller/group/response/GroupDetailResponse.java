package basakan.fryday.controller.group.response;

import basakan.fryday.domain.group.FryGroup;
import basakan.fryday.domain.group.GroupRole;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
public class GroupDetailResponse {

    private final Long groupId;
    private final String name;
    private final String inviteCode;
    private final int memberCount;
    private final int maxMemberCount;
    private final GroupRole myRole;
    private final int myPublicCategoryCount;

    /** 집계 기준일(Asia/Seoul). 앱이 자정 전환을 판단할 수 있도록 함께 내려준다. */
    private final LocalDate date;

    private final List<GroupMemberResponse> members;

    private GroupDetailResponse(FryGroup group, GroupRole myRole, int myPublicCategoryCount,
                                LocalDate date, List<GroupMemberResponse> members) {
        this.groupId = group.getId();
        this.name = group.getName();
        this.inviteCode = group.getInviteCode();
        this.memberCount = members.size();
        this.maxMemberCount = FryGroup.MAX_MEMBER_COUNT;
        this.myRole = myRole;
        this.myPublicCategoryCount = myPublicCategoryCount;
        this.date = date;
        this.members = members;
    }

    public static GroupDetailResponse of(FryGroup group, GroupRole myRole, int myPublicCategoryCount,
                                         LocalDate date, List<GroupMemberResponse> members) {
        return new GroupDetailResponse(group, myRole, myPublicCategoryCount, date, members);
    }
}
