package basakan.fryday.service.group;

import basakan.fryday.common.service.push.PushService;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.auth.UserJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

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
class GroupPushSenderTest {

    private static final Map<String, String> DATA = Map.of("type", "GROUP_JOINED", "groupId", "12");

    @Mock private PushService pushService;
    @Mock private UserJpaRepository userJpaRepository;

    @InjectMocks private GroupPushSender sender;

    @Test
    @DisplayName("수신자마다 같은 제목, 본문, 데이터로 보낸다")
    void sendsToEveryRecipient() {
        // given
        User owner = user("owner");
        User member = user("member");
        given(userJpaRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(owner, member));

        // when
        sender.send(List.of(1L, 2L), "바삭한 사람들", "본문", DATA);

        // then
        then(pushService).should().sendToUser(owner, "바삭한 사람들", "본문", DATA);
        then(pushService).should().sendToUser(member, "바삭한 사람들", "본문", DATA);
    }

    @Test
    @DisplayName("수신자가 없으면 사용자 조회도 발송도 하지 않는다")
    void skipsWhenNoRecipients() {
        // when
        sender.send(List.of(), "바삭한 사람들", "본문", DATA);

        // then
        then(userJpaRepository).should(never()).findAllById(any());
        then(pushService).should(never()).sendToUser(any(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("한 명에게 발송이 실패해도 나머지에게는 계속 보낸다")
    void continuesAfterFailure() {
        // given
        given(userJpaRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(user("first"), user("second")));
        willThrow(new RuntimeException("FCM 오류"))
                .given(pushService).sendToUser(any(), anyString(), anyString(), anyMap());

        // when
        sender.send(List.of(1L, 2L), "바삭한 사람들", "본문", DATA);

        // then
        then(pushService).should(times(2)).sendToUser(any(), anyString(), anyString(), anyMap());
    }

    private User user(String providerUserId) {
        return User.createNewUser(AuthProvider.KAKAO, providerUserId, providerUserId + "@test.com");
    }
}
