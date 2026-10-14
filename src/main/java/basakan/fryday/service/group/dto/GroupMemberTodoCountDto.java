package basakan.fryday.service.group.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 그룹원 한 명의 특정 날짜 투두 집계. 공개 카테고리에 속한 투두만 센다. */
@Getter
@AllArgsConstructor
public class GroupMemberTodoCountDto {
    private Long userId;
    private int totalCount;
    private int completedCount;
}
