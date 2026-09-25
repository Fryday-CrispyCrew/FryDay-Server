package basakan.fryday.service.group.event;

import basakan.fryday.domain.group.GroupInteractionType;

public record GroupInteractionEvent(Long groupId, String groupName, Long targetUserId, String targetNickname,
                                    GroupInteractionType type) {
}
