package basakan.fryday.service.group.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/** 그룹 참여자. joinedAt 오름차순이 곧 참여 순서다. */
@Getter
@AllArgsConstructor
public class GroupMemberDto {
    private Long userId;
    private String nickname;
    private LocalDateTime joinedAt;
}
