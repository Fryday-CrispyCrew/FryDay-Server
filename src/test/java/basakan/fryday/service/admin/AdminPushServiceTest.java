package basakan.fryday.service.admin;

import basakan.fryday.common.service.push.PushService;
import basakan.fryday.domain.user.UserDevice;
import basakan.fryday.repository.auth.UserDeviceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AdminPushServiceTest {

    @InjectMocks
    private AdminPushService adminPushService;

    @Mock
    private UserDeviceRepository userDeviceRepository;

    @Mock
    private PushService pushService;

    @Test
    @DisplayName("푸시 동의한 모든 활성 디바이스에 알림을 발송한다")
    void broadcastToAllActiveDevices() {
        // given
        UserDevice device1 = createDevice("token-1");
        UserDevice device2 = createDevice("token-2");
        given(userDeviceRepository.findAllByIsActiveTrueAndPushNotificationAgreedTrueAndFcmTokenIsNotNull(any(Pageable.class)))
                .willReturn(new SliceImpl<>(List.of(device1, device2), PageRequest.of(0, 500), false));

        // when
        int count = adminPushService.broadcastPush("공지", "서버 점검입니다");

        // then
        then(pushService).should(times(1)).sendToToken(eq("token-1"), eq("공지"), eq("서버 점검입니다"), anyMap());
        then(pushService).should(times(1)).sendToToken(eq("token-2"), eq("공지"), eq("서버 점검입니다"), anyMap());
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("대상 디바이스가 없으면 0을 반환한다")
    void noDevices() {
        // given
        given(userDeviceRepository.findAllByIsActiveTrueAndPushNotificationAgreedTrueAndFcmTokenIsNotNull(any(Pageable.class)))
                .willReturn(new SliceImpl<>(List.of(), PageRequest.of(0, 500), false));

        // when
        int count = adminPushService.broadcastPush("공지", "내용");

        // then
        then(pushService).should(never()).sendToToken(anyString(), anyString(), anyString(), anyMap());
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("일부 디바이스 발송 실패해도 나머지는 계속 발송한다")
    void partialFailureContinues() {
        // given
        UserDevice device1 = createDevice("token-1");
        UserDevice device2 = createDevice("token-2");
        UserDevice device3 = createDevice("token-3");
        given(userDeviceRepository.findAllByIsActiveTrueAndPushNotificationAgreedTrueAndFcmTokenIsNotNull(any(Pageable.class)))
                .willReturn(new SliceImpl<>(List.of(device1, device2, device3), PageRequest.of(0, 500), false));
        org.mockito.Mockito.lenient().doThrow(new RuntimeException("FCM error"))
                .when(pushService).sendToToken(eq("token-2"), eq("공지"), eq("내용"), anyMap());

        // when
        int count = adminPushService.broadcastPush("공지", "내용");

        // then
        then(pushService).should().sendToToken(eq("token-1"), eq("공지"), eq("내용"), anyMap());
        then(pushService).should().sendToToken(eq("token-2"), eq("공지"), eq("내용"), anyMap());
        then(pushService).should().sendToToken(eq("token-3"), eq("공지"), eq("내용"), anyMap());
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("동일한 FCM 토큰을 가진 디바이스가 여러 개여도 알림은 1회만 발송한다")
    void broadcastWithDuplicateTokens_sendsOnce() {
        // given
        UserDevice device1 = createDevice("token-dup");
        UserDevice device2 = createDeviceWithUserId(2L, "token-dup");
        UserDevice device3 = createDevice("token-unique");
        given(userDeviceRepository.findAllByIsActiveTrueAndPushNotificationAgreedTrueAndFcmTokenIsNotNull(any(Pageable.class)))
                .willReturn(new SliceImpl<>(List.of(device1, device2, device3), PageRequest.of(0, 500), false));

        // when
        int count = adminPushService.broadcastPush("공지", "내용");

        // then
        then(pushService).should(times(1)).sendToToken(eq("token-dup"), eq("공지"), eq("내용"), anyMap());
        then(pushService).should(times(1)).sendToToken(eq("token-unique"), eq("공지"), eq("내용"), anyMap());
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("모든 기기에 어드민 발송 종류와, 이번 발송에서 하나만 만든 pushId 를 담아 보낸다")
    void broadcastWithTypeAndSharedPushId() {
        // given
        UserDevice device1 = createDevice("token-1");
        UserDevice device2 = createDeviceWithUserId(2L, "token-2");
        given(userDeviceRepository.findAllByIsActiveTrueAndPushNotificationAgreedTrueAndFcmTokenIsNotNull(any(Pageable.class)))
                .willReturn(new SliceImpl<>(List.of(device1, device2), PageRequest.of(0, 500), false));

        // when
        adminPushService.broadcastPush("공지", "내용");

        // then
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        then(pushService).should(times(2)).sendToToken(anyString(), anyString(), anyString(), captor.capture());
        List<Map<String, String>> sentData = captor.getAllValues();
        assertThat(sentData).allSatisfy(data -> assertThat(data)
                .containsEntry("type", "ADMIN_BROADCAST")
                .containsKey("pushId"));
        assertThat(sentData.get(0).get("pushId")).isEqualTo(sentData.get(1).get("pushId"));
    }

    private UserDevice createDevice(String fcmToken) {
        return createDeviceWithUserId(1L, fcmToken);
    }

    private UserDevice createDeviceWithUserId(Long userId, String fcmToken) {
        UserDevice device = UserDevice.builder()
                .userId(userId)
                .deviceId("device-" + userId + "-" + fcmToken)
                .fcmToken(fcmToken)
                .deviceType("iOS")
                .deviceName("Test Device")
                .build();
        device.updatePushNotificationAgreement(true);
        return device;
    }
}
