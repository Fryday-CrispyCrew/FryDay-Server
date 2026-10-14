package basakan.fryday.service.group.event;

import java.util.List;

public record GroupJoinedEvent(Long groupId, String groupName, String joinerNickname, List<Long> recipientIds) {
}
