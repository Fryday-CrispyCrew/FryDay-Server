package basakan.fryday.service.group;

import basakan.fryday.domain.group.GroupInteractionType;
import basakan.fryday.service.group.event.GroupDisbandedEvent;
import basakan.fryday.service.group.event.GroupInteractionEvent;
import basakan.fryday.service.group.event.GroupJoinedEvent;
import basakan.fryday.service.group.event.GroupProgressChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("그룹 알림 리스너")
class GroupNotificationListenerTest {

    private static final Long GROUP_ID = 12L;

    @Mock private GroupPushSender groupPushSender;
    @Mock private GroupProgressNotifier groupProgressNotifier;

    @InjectMocks private GroupNotificationListener listener;

    @Test
    @DisplayName("참여 알림은 그룹명을 제목으로, 참여자 닉네임을 본문에 담는다")
    void sendsJoinedNotification() {
        // when
        listener.onGroupJoined(new GroupJoinedEvent(GROUP_ID, "바삭한 사람들", "지민", List.of(1L, 2L)));

        // then
        then(groupPushSender).should().send(List.of(1L, 2L), "바삭한 사람들", "지민님이 그룹에 참여했습니다.",
                Map.of("type", "GROUP_JOINED", "groupId", "12"));
    }

    @Test
    @DisplayName("참여자 닉네임이 없으면 '새 그룹원'으로 표기한다")
    void usesFallbackWhenNicknameMissing() {
        // when
        listener.onGroupJoined(new GroupJoinedEvent(GROUP_ID, "바삭한 사람들", null, List.of(1L)));

        // then
        then(groupPushSender).should().send(List.of(1L), "바삭한 사람들", "새 그룹원님이 그룹에 참여했습니다.",
                Map.of("type", "GROUP_JOINED", "groupId", "12"));
    }

    @Test
    @DisplayName("해체 알림은 그룹명 받침에 맞춰 조사를 붙이고, 사라진 그룹의 ID는 싣지 않는다")
    void sendsDisbandedNotification() {
        // when
        listener.onGroupDisbanded(new GroupDisbandedEvent("바삭한 사람들", List.of(2L)));

        // then
        then(groupPushSender).should().send(List.of(2L), "바삭한 사람들", "바삭한 사람들이 해체되었습니다.",
                Map.of("type", "GROUP_DISBANDED"));
    }

    @Test
    @DisplayName("진행 상태가 바뀌면 해당 사용자의 튀기기 시작과 영업종료 여부를 판단한다")
    void delegatesProgressChange() {
        // when
        listener.onGroupProgressChanged(new GroupProgressChangedEvent(7L));

        // then
        then(groupProgressNotifier).should().notifyProgress(7L);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "KNOCK, 연우님이 똑똑똑 두드렸어요!",
            "ORDER, 연우님이 주문을 넣었어요!",
            "DELICIOUS, 연우님이 맛있대요!",
            "APPLAUSE, 연우님이 박수를 보냈어요!"
    })
    @DisplayName("상호작용 알림은 받는 사람 한 명에게 버튼별 문구와 상호작용 종류를 담아 보낸다")
    void sendsInteractionNotification(GroupInteractionType type, String body) {
        // when
        listener.onGroupInteraction(new GroupInteractionEvent(GROUP_ID, "바삭한 사람들", "연우", 5L, type));

        // then
        then(groupPushSender).should().send(List.of(5L), "바삭한 사람들", body,
                Map.of("type", "GROUP_INTERACTION", "groupId", "12", "interactionType", type.name()));
    }

    @Test
    @DisplayName("보낸 사람 닉네임이 없으면 '그룹원'으로 표기한다")
    void interactionUsesFallbackNickname() {
        // when
        listener.onGroupInteraction(new GroupInteractionEvent(
                GROUP_ID, "바삭한 사람들", null, 5L, GroupInteractionType.KNOCK));

        // then
        then(groupPushSender).should().send(List.of(5L), "바삭한 사람들", "그룹원님이 똑똑똑 두드렸어요!",
                Map.of("type", "GROUP_INTERACTION", "groupId", "12", "interactionType", "KNOCK"));
    }

    @Test
    @DisplayName("받침이 없으면 '가', 한글이 아니면 '이(가)'를 붙인다")
    void subjectParticle() {
        assertThat(GroupNotificationListener.subjectParticle("사람들")).isEqualTo("이");
        assertThat(GroupNotificationListener.subjectParticle("스터디")).isEqualTo("가");
        assertThat(GroupNotificationListener.subjectParticle("FRY")).isEqualTo("이(가)");
    }
}
