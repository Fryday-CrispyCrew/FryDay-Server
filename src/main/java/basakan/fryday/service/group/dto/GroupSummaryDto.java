package basakan.fryday.service.group.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 내가 참여 중인 그룹 한 건. 조회자가 그룹장인지는 ownerId 와 비교해 판단한다. */
@Getter
@AllArgsConstructor
public class GroupSummaryDto {
    private Long groupId;
    private String name;
    private Long ownerId;
    private long memberCount;
}
