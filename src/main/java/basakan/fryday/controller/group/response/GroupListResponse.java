package basakan.fryday.controller.group.response;

import lombok.Getter;

import java.util.List;

@Getter
public class GroupListResponse {

    private final List<GroupSummaryResponse> groups;

    private GroupListResponse(List<GroupSummaryResponse> groups) {
        this.groups = groups;
    }

    public static GroupListResponse from(List<GroupSummaryResponse> groups) {
        return new GroupListResponse(groups);
    }
}
