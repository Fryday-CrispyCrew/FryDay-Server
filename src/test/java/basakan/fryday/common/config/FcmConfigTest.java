package basakan.fryday.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@DisplayName("FCM 설정")
class FcmConfigTest {

    @Test
    @DisplayName("FCM 이 꺼져 있으면 인증 정보가 없어도 기동한다")
    void disabledStartsWithoutCredentials() {
        FcmConfig config = fcmConfig(false, null);

        assertThatCode(config::initialize).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FCM 이 켜져 있는데 인증 정보가 없으면 기동을 실패시킨다")
    void enabledWithoutCredentialsFails() {
        // 로컬에 인증 파일을 둔 개발 환경에서는 검증할 수 없다
        assumeFalse(new ClassPathResource("firebase-adminsdk.json").exists());
        FcmConfig config = fcmConfig(true, null);

        assertThatThrownBy(config::initialize)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("인증 정보가 없다");
    }

    @Test
    @DisplayName("FCM_CREDENTIALS_JSON 이 공백뿐이면 인증 정보가 없는 것으로 보고 기동을 실패시킨다")
    void enabledWithBlankCredentialsFails() {
        assumeFalse(new ClassPathResource("firebase-adminsdk.json").exists());
        FcmConfig config = fcmConfig(true, "   ");

        assertThatThrownBy(config::initialize)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("인증 정보가 없다");
    }

    @Test
    @DisplayName("FCM 이 켜져 있는데 인증 정보를 읽지 못하면 기동을 실패시킨다")
    void enabledWithInvalidCredentialsFails() {
        FcmConfig config = fcmConfig(true, "{\"type\":\"service_account\"}");

        assertThatThrownBy(config::initialize)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("초기화하지 못했다");
    }

    private FcmConfig fcmConfig(boolean enabled, String credentialsJson) {
        FcmConfig config = new FcmConfig();
        ReflectionTestUtils.setField(config, "fcmEnabled", enabled);
        ReflectionTestUtils.setField(config, "credentialsJson", credentialsJson);
        return config;
    }
}
