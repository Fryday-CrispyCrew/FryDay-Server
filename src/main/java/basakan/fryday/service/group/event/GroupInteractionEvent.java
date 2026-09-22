package basakan.fryday.service.group.event;

import basakan.fryday.domain.group.GroupInteractionType;

public record GroupInteractionEvent(Long groupId, String groupName, String senderNickname, Long targetUserId,
                                    GroupInteractionType type) {
}
