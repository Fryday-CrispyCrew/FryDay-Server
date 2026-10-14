package basakan.fryday.service.group.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 한 사용자가 특정 그룹에 공개한 카테고리 기준의 특정 날짜 투두 집계. */
@Getter
@AllArgsConstructor
public class GroupProgressDto {
    private Long groupId;
    private int totalCount;
    private int completedCount;

    public boolean isAllCompleted() {
        return totalCount > 0 && completedCount == totalCount;
    }
}
