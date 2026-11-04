package basakan.fryday.common.service.push;

import basakan.fryday.domain.eventlog.EventType;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.User;
import basakan.fryday.domain.user.UserDevice;
import basakan.fryday.repository.auth.UserDeviceRepository;
import basakan.fryday.service.eventlog.UserEvent;
import basakan.fryday.service.fcm.UserDeviceWriteService;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.springframework.test.util.ReflectionTestUtils.getField;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@ExtendWith(MockitoExtension.class)
@DisplayName("FCM 유저 발송")
class FcmPushServiceSendTest {

    private static final Long USER_ID = 7L;

    @Mock private UserDeviceRepository userDeviceRepository;
    @Mock private UserDeviceWriteService userDeviceWriteService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private FcmPushService fcmPushService;

    private final FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);
    private MockedStatic<FirebaseMessaging> firebaseMessagingStatic;
    private User user;

    @BeforeEach
    void setUp() {
        setField(fcmPushService, "fcmEnabled", true);
        firebaseMessagingStatic = mockStatic(FirebaseMessaging.class);
        firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
        user = User.createNewUser(AuthProvider.KAKAO, "push-user", "push@test.com");
        setField(user, "id", USER_ID);
    }

    @AfterEach
    void tearDown() {
        firebaseMessagingStatic.close();
    }

    @Test
    @DisplayName("기기 여러 대에 같은 pushId 를 붙여 보내고, 발송 기록은 유저당 한 건 남긴다")
    void sendsSamePushIdToAllDevicesAndRecordsOnce() throws Exception {
        // given
        givenDevices(device(1L, "token-1"), device(2L, "token-2"));

        // when
        fcmPushService.sendToUser(user, "제목", "본문", Map.of("type", "TODO_ALARM", "todoId", "30"));

        // then
        List<Map<String, String>> sentData = sentData(2);
        String pushId = sentData.get(0).get("pushId");
        assertThat(pushId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(sentData).allSatisfy(data -> assertThat(data)
                .containsEntry("type", "TODO_ALARM")
                .containsEntry("todoId", "30")
                .containsEntry("pushId", pushId));

        UserEvent event = publishedEvent();
        assertThat(event.userId()).isEqualTo(USER_ID);
        assertThat(event.type()).isEqualTo(EventType.PUSH_SENT);
        assertThat(event.targetId()).isEqualTo(30L);
        assertThat(event.properties()).isEqualTo(Map.of("type", "TODO_ALARM", "pushId", pushId));
    }

    @Test
    @DisplayName("그룹 푸시는 그룹 ID 를, 대상 ID 가 없는 푸시는 null 을 대상으로 기록한다")
    void recordsGroupIdOrNullAsTarget() throws Exception {
        // given
        givenDevices(device(1L, "token-1"));

        // when
        fcmPushService.sendToUser(user, "그룹", "본문", Map.of("type", "GROUP_JOINED", "groupId", "5"));
        fcmPushService.sendToUser(user, "그룹", "본문", Map.of("type", "GROUP_DISBANDED"));

        // then
        ArgumentCaptor<UserEvent> captor = ArgumentCaptor.forClass(UserEvent.class);
        then(eventPublisher).should(times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues()).extracting(UserEvent::targetId).containsExactly(5L, null);
    }

    @Test
    @DisplayName("대상 ID 가 숫자가 아니어도 발송은 성공으로 끝나고, 대상 없이 기록한다")
    void nonNumericTargetIdDoesNotFailSend() throws Exception {
        // given
        givenDevices(device(1L, "token-1"));

        // when
        fcmPushService.sendToUser(user, "그룹", "본문", Map.of("type", "GROUP_JOINED", "groupId", "abc"));

        // then
        sentData(1);
        assertThat(publishedEvent().targetId()).isNull();
    }

    @Test
    @DisplayName("발송할 때마다 새 pushId 를 만든다")
    void createsNewPushIdPerSend() throws Exception {
        // given
        givenDevices(device(1L, "token-1"));

        // when
        fcmPushService.sendToUser(user, "제목", "본문", Map.of("type", "TODO_BURNT"));
        fcmPushService.sendToUser(user, "제목", "본문", Map.of("type", "TODO_BURNT"));

        // then
        List<Map<String, String>> sentData = sentData(2);
        assertThat(sentData.get(0).get("pushId")).isNotEqualTo(sentData.get(1).get("pushId"));
    }

    @Test
    @DisplayName("한 대라도 보내지면 기록하고, 모든 기기에 보내지 못하면 기록하지 않는다")
    void recordsOnlyWhenAtLeastOneDeviceSucceeds() throws Exception {
        // given
        givenDevices(device(1L, "token-1"), device(2L, "token-2"));
        given(firebaseMessaging.send(any(Message.class)))
                .willThrow(new IllegalStateException("fail"))
                .willReturn("ok")
                .willThrow(new IllegalStateException("fail"));

        // when
        fcmPushService.sendToUser(user, "제목", "본문", Map.of("type", "TODO_DEADLINE"));
        fcmPushService.sendToUser(user, "제목", "본문", Map.of("type", "TODO_DEADLINE"));

        // then
        then(eventPublisher).should(times(1)).publishEvent(any(UserEvent.class));
    }

    @Test
    @DisplayName("보낼 기기가 없으면 보내지도 기록하지도 않는다")
    void noDevicesNoRecord() throws Exception {
        // given
        givenDevices();

        // when
        fcmPushService.sendToUser(user, "제목", "본문", Map.of("type", "TODO_ENCOURAGE"));

        // then
        then(firebaseMessaging).should(never()).send(any(Message.class));
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("FCM 이 꺼져 있으면 기록하지 않는다")
    void fcmDisabledNoRecord() {
        // given
        setField(fcmPushService, "fcmEnabled", false);

        // when
        fcmPushService.sendToUser(user, "제목", "본문", Map.of("type", "TODO_ENCOURAGE"));

        // then
        then(eventPublisher).should(never()).publishEvent(any());
    }

    private void givenDevices(UserDevice... devices) {
        given(userDeviceRepository.findAllByUserIdAndIsActiveTrueAndPushNotificationAgreedTrue(USER_ID))
                .willReturn(List.of(devices));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> sentData(int expectedSends) throws Exception {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        then(firebaseMessaging).should(times(expectedSends)).send(captor.capture());
        return captor.getAllValues().stream()
                .map(message -> (Map<String, String>) getField(message, "data"))
                .toList();
    }

    private UserEvent publishedEvent() {
        ArgumentCaptor<UserEvent> captor = ArgumentCaptor.forClass(UserEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        return captor.getValue();
    }

    private UserDevice device(Long id, String token) {
        UserDevice device = UserDevice.builder()
                .userId(USER_ID)
                .deviceId("device-" + id)
                .fcmToken(token)
                .deviceType("iOS")
                .deviceName("Test Device")
                .build();
        setField(device, "id", id);
        return device;
    }
}
