package basakan.fryday.domain.group;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 그룹원의 오늘 영업 상태. 공개 카테고리의 오늘 투두 개수로 정하며, 상태마다 보낼 수 있는 상호작용이 하나씩 정해져 있다.
 */
@Getter
@RequiredArgsConstructor
public enum GroupMemberStatus {
    BEFORE_OPEN(GroupInteractionType.KNOCK),
    PREPARING(GroupInteractionType.ORDER),
    FRYING(GroupInteractionType.DELICIOUS),
    CLOSED(GroupInteractionType.APPLAUSE);

    private final GroupInteractionType availableInteraction;

    public static GroupMemberStatus of(int totalCount, int completedCount) {
        if (totalCount == 0) {
            return BEFORE_OPEN;
        }
        if (completedCount == 0) {
            return PREPARING;
        }
        return completedCount < totalCount ? FRYING : CLOSED;
    }
}
