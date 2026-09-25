package basakan.fryday.service.group;

import basakan.fryday.domain.group.GroupInteractionType;
import basakan.fryday.domain.group.GroupPushType;
import basakan.fryday.service.group.event.GroupDisbandedEvent;
import basakan.fryday.service.group.event.GroupInteractionEvent;
import basakan.fryday.service.group.event.GroupJoinedEvent;
import basakan.fryday.service.group.event.GroupProgressChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GroupNotificationListener {

    private static final String UNKNOWN_NICKNAME = "새 그룹원";
    private static final String GROUP_MEMBER_NICKNAME = "그룹원";
    private static final String TYPE_KEY = "type";
    private static final String GROUP_ID_KEY = "groupId";
    private static final String INTERACTION_TYPE_KEY = "interactionType";

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

    @Async("pushAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGroupInteraction(GroupInteractionEvent event) {
        String nickname = event.senderNickname() != null ? event.senderNickname() : GROUP_MEMBER_NICKNAME;
        groupPushSender.send(List.of(event.targetUserId()), event.groupName(),
                nickname + interactionMessage(event.type()),
                Map.of(TYPE_KEY, GroupPushType.GROUP_INTERACTION.name(),
                        GROUP_ID_KEY, String.valueOf(event.groupId()),
                        INTERACTION_TYPE_KEY, event.type().name()));
    }

    private static String interactionMessage(GroupInteractionType type) {
        return switch (type) {
            case KNOCK -> "님, 손님 왔어요!";
            case ORDER -> "님, 주문이요!";
            case DELICIOUS -> "님, 추가 주문할게요!";
            case APPLAUSE -> "님, 별점 5점 드릴게요!";
        };
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
