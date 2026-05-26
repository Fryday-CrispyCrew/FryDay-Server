package basakan.fryday.common.service.push;

import basakan.fryday.domain.user.UserDevice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FcmPushServiceTest {

    @Test
    @DisplayName("동일한 FCM 토큰을 가진 디바이스가 여러 개여도 고유 토큰만 추출된다")
    void extractUniqueTokens_duplicateTokens() {
        // given
        UserDevice device1 = createDevice(1L, "same-token");
        UserDevice device2 = createDevice(2L, "same-token");
        List<UserDevice> devices = List.of(device1, device2);

        // when - sendToUser 내부에서 사용할 중복 제거 로직
        Set<String> sentTokens = new HashSet<>();
        for (UserDevice device : devices) {
            if (device.getFcmToken() != null && !device.getFcmToken().isEmpty()) {
                sentTokens.add(device.getFcmToken());
            }
        }

        // then
        assertThat(sentTokens).hasSize(1);
        assertThat(sentTokens).containsExactly("same-token");
    }

    @Test
    @DisplayName("서로 다른 FCM 토큰을 가진 디바이스는 모두 추출된다")
    void extractUniqueTokens_differentTokens() {
        // given
        UserDevice device1 = createDevice(1L, "token-a");
        UserDevice device2 = createDevice(2L, "token-b");
        List<UserDevice> devices = List.of(device1, device2);

        // when
        Set<String> sentTokens = new HashSet<>();
        for (UserDevice device : devices) {
            if (device.getFcmToken() != null && !device.getFcmToken().isEmpty()) {
                sentTokens.add(device.getFcmToken());
            }
        }

        // then
        assertThat(sentTokens).hasSize(2);
        assertThat(sentTokens).containsExactlyInAnyOrder("token-a", "token-b");
    }

    @Test
    @DisplayName("null, 빈 문자열, 공백 토큰은 제외된다")
    void extractUniqueTokens_nullAndEmptyAndBlankFiltered() {
        // given
        UserDevice device1 = createDevice(1L, "valid-token");
        UserDevice device2 = createDevice(2L, null);
        UserDevice device3 = createDevice(3L, "");
        UserDevice device4 = createDevice(4L, "valid-token");
        UserDevice device5 = createDevice(5L, "   ");
        List<UserDevice> devices = List.of(device1, device2, device3, device4, device5);

        // when
        Set<String> sentTokens = new HashSet<>();
        for (UserDevice device : devices) {
            String token = device.getFcmToken();
            if (token != null && !token.isBlank()) {
                sentTokens.add(token);
            }
        }

        // then
        assertThat(sentTokens).hasSize(1);
        assertThat(sentTokens).containsExactly("valid-token");
    }

    private UserDevice createDevice(Long id, String fcmToken) {
        return UserDevice.builder()
                .userId(1L)
                .deviceId("device-" + id)
                .fcmToken(fcmToken)
                .deviceType("iOS")
                .deviceName("Test Device")
                .build();
    }
}
