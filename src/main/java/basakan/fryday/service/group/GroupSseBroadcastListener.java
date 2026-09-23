package basakan.fryday.service.group;

import basakan.fryday.repository.group.GroupMemberRepository;
import basakan.fryday.service.group.event.GroupProgressChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class GroupSseBroadcastListener {

    private final GroupSseService groupSseService;
    private final GroupMemberRepository groupMemberRepository;

    @Async("pushAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onGroupProgressChanged(GroupProgressChangedEvent event) {
        if (groupSseService.isEmpty()) {
            return;
        }
        groupMemberRepository.findGroupIdsByUserId(event.userId())
                .forEach(groupId -> groupSseService.broadcast(groupId, event.userId()));
    }
}
