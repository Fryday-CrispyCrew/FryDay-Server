package basakan.fryday.service.group.event;

import java.util.List;

public record GroupDisbandedEvent(String groupName, List<Long> recipientIds) {
}
