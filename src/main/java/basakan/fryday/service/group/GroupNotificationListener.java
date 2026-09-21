package basakan.fryday.service.group;

import basakan.fryday.common.service.push.PushService;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.auth.UserJpaRepository;
import basakan.fryday.service.group.event.GroupDisbandedEvent;
import basakan.fryday.service.group.event.GroupJoinedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GroupNotificationListener {

    private static final String UNKNOWN_NICKNAME = "새 그룹원";
    private static final String TYPE_KEY = "type";
    private static final String GROUP_ID_KEY = "groupId";

    private final PushService pushService;
    private final UserJpaRepository userJpaRepository;

    @Async("pushAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGroupJoined(GroupJoinedEvent event) {
        String nickname = event.joinerNickname() != null ? event.joinerNickname() : UNKNOWN_NICKNAME;
        send(event.recipientIds(), event.groupName(), nickname + "님이 그룹에 참여했습니다.",
                Map.of(TYPE_KEY, GroupPushType.GROUP_JOINED.name(),
                        GROUP_ID_KEY, String.valueOf(event.groupId())));
    }

    @Async("pushAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGroupDisbanded(GroupDisbandedEvent event) {
        String groupName = event.groupName();
        send(event.recipientIds(), groupName, groupName + subjectParticle(groupName) + " 해체되었습니다.",
                Map.of(TYPE_KEY, GroupPushType.GROUP_DISBANDED.name()));
    }

    private void send(List<Long> recipientIds, String title, String body, Map<String, String> data) {
        if (recipientIds.isEmpty()) {
            return;
        }
        for (User user : userJpaRepository.findAllById(recipientIds)) {
            try {
                pushService.sendToUser(user, title, body, data);
            } catch (RuntimeException e) {
                log.warn("그룹 알림 발송 실패: userId={}", user.getId(), e);
            }
        }
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
