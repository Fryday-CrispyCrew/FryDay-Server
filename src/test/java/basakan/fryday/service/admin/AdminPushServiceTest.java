package basakan.fryday.service.admin;

import basakan.fryday.common.service.push.PushService;
import basakan.fryday.domain.user.UserDevice;
import basakan.fryday.repository.auth.UserDeviceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
        then(pushService).should(times(1)).sendToToken("token-1", "공지", "서버 점검입니다");
        then(pushService).should(times(1)).sendToToken("token-2", "공지", "서버 점검입니다");
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
        then(pushService).should(never()).sendToToken(anyString(), anyString(), anyString());
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
                .when(pushService).sendToToken("token-2", "공지", "내용");

        // when
        int count = adminPushService.broadcastPush("공지", "내용");

        // then
        then(pushService).should().sendToToken("token-1", "공지", "내용");
        then(pushService).should().sendToToken("token-2", "공지", "내용");
        then(pushService).should().sendToToken("token-3", "공지", "내용");
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
        then(pushService).should(times(1)).sendToToken("token-dup", "공지", "내용");
        then(pushService).should(times(1)).sendToToken("token-unique", "공지", "내용");
        assertThat(count).isEqualTo(2);
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
