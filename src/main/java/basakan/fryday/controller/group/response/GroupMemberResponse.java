package basakan.fryday.controller.group.response;

import basakan.fryday.domain.group.GroupRole;
import basakan.fryday.service.group.dto.GroupMemberDto;
import basakan.fryday.service.group.dto.GroupMemberTodoCountDto;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;

@Getter
public class GroupMemberResponse {

    private final Long userId;
    private final String nickname;
    private final GroupRole role;
    private final int totalCount;
    private final int completedCount;

    private GroupMemberResponse(GroupMemberDto member, GroupRole role, GroupMemberTodoCountDto todoCount) {
        this.userId = member.getUserId();
        this.nickname = member.getNickname();
        this.role = role;
        this.totalCount = (todoCount == null) ? 0 : todoCount.getTotalCount();
        this.completedCount = (todoCount == null) ? 0 : todoCount.getCompletedCount();
    }

    /** todoCount 가 null 이면 오늘 공개된 투두가 하나도 없는 그룹원이다. */
    public static GroupMemberResponse of(GroupMemberDto member, GroupRole role, GroupMemberTodoCountDto todoCount) {
        return new GroupMemberResponse(member, role, todoCount);
    }

    /** 그룹장을 목록 맨 앞에 두기 위한 정렬용. 응답에는 role 로만 노출한다. */
    @JsonIgnore
    public boolean isOwner() {
        return this.role == GroupRole.OWNER;
    }
}
