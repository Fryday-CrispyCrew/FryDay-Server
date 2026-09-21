package basakan.fryday.service.group;

import basakan.fryday.common.service.push.PushService;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.auth.UserJpaRepository;
import basakan.fryday.service.group.event.GroupDisbandedEvent;
import basakan.fryday.service.group.event.GroupJoinedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("그룹 알림 발송")
class GroupNotificationListenerTest {

    private static final Long GROUP_ID = 12L;

    @Mock private PushService pushService;
    @Mock private UserJpaRepository userJpaRepository;

    @InjectMocks private GroupNotificationListener listener;

    @Test
    @DisplayName("참여 알림은 그룹명을 제목으로, 참여자 닉네임을 본문에 담아 수신자마다 보낸다")
    void sendsJoinedNotification() {
        // given
        User owner = user("owner");
        User member = user("member");
        given(userJpaRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(owner, member));

        // when
        listener.onGroupJoined(new GroupJoinedEvent(GROUP_ID, "바삭한 사람들", "지민", List.of(1L, 2L)));

        // then
        Map<String, String> data = Map.of("type", "GROUP_JOINED", "groupId", "12");
        then(pushService).should().sendToUser(owner, "바삭한 사람들", "지민님이 그룹에 참여했습니다.", data);
        then(pushService).should().sendToUser(member, "바삭한 사람들", "지민님이 그룹에 참여했습니다.", data);
    }

    @Test
    @DisplayName("참여자 닉네임이 없으면 '새 그룹원'으로 표기한다")
    void usesFallbackWhenNicknameMissing() {
        // given
        User owner = user("owner");
        given(userJpaRepository.findAllById(List.of(1L))).willReturn(List.of(owner));

        // when
        listener.onGroupJoined(new GroupJoinedEvent(GROUP_ID, "바삭한 사람들", null, List.of(1L)));

        // then
        then(pushService).should().sendToUser(
                owner, "바삭한 사람들", "새 그룹원님이 그룹에 참여했습니다.",
                Map.of("type", "GROUP_JOINED", "groupId", "12"));
    }

    @Test
    @DisplayName("해체 알림은 그룹명 받침에 맞춰 조사를 붙이고, 사라진 그룹의 ID는 싣지 않는다")
    void sendsDisbandedNotification() {
        // given
        User member = user("member");
        given(userJpaRepository.findAllById(List.of(2L))).willReturn(List.of(member));

        // when
        listener.onGroupDisbanded(new GroupDisbandedEvent("바삭한 사람들", List.of(2L)));

        // then
        then(pushService).should().sendToUser(
                member, "바삭한 사람들", "바삭한 사람들이 해체되었습니다.", Map.of("type", "GROUP_DISBANDED"));
    }

    @Test
    @DisplayName("받침이 없으면 '가', 한글이 아니면 '이(가)'를 붙인다")
    void subjectParticle() {
        assertThat(GroupNotificationListener.subjectParticle("사람들")).isEqualTo("이");
        assertThat(GroupNotificationListener.subjectParticle("스터디")).isEqualTo("가");
        assertThat(GroupNotificationListener.subjectParticle("FRY")).isEqualTo("이(가)");
    }

    @Test
    @DisplayName("수신자가 없으면 사용자 조회도 발송도 하지 않는다")
    void skipsWhenNoRecipients() {
        // when
        listener.onGroupJoined(new GroupJoinedEvent(GROUP_ID, "바삭한 사람들", "지민", List.of()));

        // then
        then(userJpaRepository).should(never()).findAllById(any());
        then(pushService).should(never()).sendToUser(any(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("한 명에게 발송이 실패해도 나머지에게는 계속 보낸다")
    void continuesAfterFailure() {
        // given
        User first = user("first");
        User second = user("second");
        given(userJpaRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(first, second));
        willThrow(new RuntimeException("FCM 오류"))
                .given(pushService).sendToUser(any(), anyString(), anyString(), anyMap());

        // when
        listener.onGroupDisbanded(new GroupDisbandedEvent("바삭한 사람들", List.of(1L, 2L)));

        // then
        then(pushService).should(times(2)).sendToUser(any(), anyString(), anyString(), anyMap());
    }

    private User user(String providerUserId) {
        return User.createNewUser(AuthProvider.KAKAO, providerUserId, providerUserId + "@test.com");
    }
}
