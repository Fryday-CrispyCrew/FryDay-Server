package basakan.fryday.common.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Firebase Cloud Messaging 설정
 * - Firebase Admin SDK 초기화
 * - firebase-adminsdk.json 파일 로드 (로컬 테스트용)
 * - 프로덕션: 환경변수 또는 Secret Manager 사용 권장
 */
@Slf4j
@Configuration
public class FcmConfig {

    private static final String DEFAULT_CREDENTIALS_PATH = "firebase-adminsdk.json";

    @Value("${fcm.enabled:false}")
    private boolean fcmEnabled;

    @Value("${fcm.credentials.json:#{null}}")
    private String credentialsJson;

    @PostConstruct
    public void initialize() {
        if (!fcmEnabled) {
            log.warn("FCM is disabled. Push notifications will not be sent.");
            return;
        }

        if (!StringUtils.hasText(credentialsJson) && !new ClassPathResource(DEFAULT_CREDENTIALS_PATH).exists()) {
            throw new IllegalStateException(
                    "fcm.enabled=true 인데 FCM 인증 정보가 없다. FCM_CREDENTIALS_JSON 을 설정하거나 "
                            + DEFAULT_CREDENTIALS_PATH + " 를 classpath 에 두어야 한다");
        }

        try (InputStream serviceAccount = openCredentials()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                log.info("Firebase Admin SDK initialized successfully");
            } else {
                log.info("Firebase Admin SDK already initialized");
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("FCM 인증 정보로 Firebase Admin SDK 를 초기화하지 못했다", e);
        }
    }

    private InputStream openCredentials() throws IOException {
        if (StringUtils.hasText(credentialsJson)) {
            log.info("Loading FCM credentials from environment variable");
            return new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8));
        }
        log.info("Loading FCM credentials from file: {}", DEFAULT_CREDENTIALS_PATH);
        return new ClassPathResource(DEFAULT_CREDENTIALS_PATH).getInputStream();
    }
}
