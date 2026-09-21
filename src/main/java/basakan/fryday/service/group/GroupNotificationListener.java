package basakan.fryday.service.group;

import basakan.fryday.domain.group.GroupPushType;
import basakan.fryday.service.group.event.GroupDisbandedEvent;
import basakan.fryday.service.group.event.GroupJoinedEvent;
import basakan.fryday.service.group.event.GroupProgressChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GroupNotificationListener {

    private static final String UNKNOWN_NICKNAME = "새 그룹원";
    private static final String TYPE_KEY = "type";
    private static final String GROUP_ID_KEY = "groupId";

    private final GroupPushSender groupPushSender;
    private final GroupProgressNotifier groupProgressNotifier;

    @Async("pushAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGroupJoined(GroupJoinedEvent event) {
        String nickname = event.joinerNickname() != null ? event.joinerNickname() : UNKNOWN_NICKNAME;
        groupPushSender.send(event.recipientIds(), event.groupName(), nickname + "님이 그룹에 참여했습니다.",
                Map.of(TYPE_KEY, GroupPushType.GROUP_JOINED.name(),
                        GROUP_ID_KEY, String.valueOf(event.groupId())));
    }

    @Async("pushAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGroupDisbanded(GroupDisbandedEvent event) {
        String groupName = event.groupName();
        groupPushSender.send(event.recipientIds(), groupName, groupName + subjectParticle(groupName) + " 해체되었습니다.",
                Map.of(TYPE_KEY, GroupPushType.GROUP_DISBANDED.name()));
    }

    @Async("pushAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onGroupProgressChanged(GroupProgressChangedEvent event) {
        groupProgressNotifier.notifyProgress(event.userId());
    }

    static String subjectParticle(String word) {
        char last = word.charAt(word.length() - 1);
        if (last < '가' || last > '힣') {
            return "이(가)";
        }
        // 한글 음절은 받침 28가지 단위로 배열되어 있어, 나머지가 0이면 받침이 없다
        return (last - '가') % 28 == 0 ? "가" : "이";
    }
}
