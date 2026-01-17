package basakan.fryday.common.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

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

        try {
            InputStream serviceAccount;

            // 1. 환경변수로 JSON 내용이 제공된 경우 우선 사용 (운영 환경)
            if (credentialsJson != null && !credentialsJson.isEmpty()) {
                log.info("Loading FCM credentials from environment variable");
                serviceAccount = new ByteArrayInputStream(credentialsJson.getBytes());
            }
            // 2. 파일 경로로 로드 (로컬 환경)
            else {
                log.info("Loading FCM credentials from file: {}", DEFAULT_CREDENTIALS_PATH);
                ClassPathResource resource = new ClassPathResource(DEFAULT_CREDENTIALS_PATH);

                if (!resource.exists()) {
                    log.error("FCM credentials file not found: {}", DEFAULT_CREDENTIALS_PATH);
                    log.error("Push notifications will not work. Please add {} to src/main/resources/", DEFAULT_CREDENTIALS_PATH);
                    return;
                }

                serviceAccount = resource.getInputStream();
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                log.info("Firebase Admin SDK initialized successfully");
            } else {
                log.info("Firebase Admin SDK already initialized");
            }

        } catch (IOException e) {
            log.error("Failed to initialize Firebase Admin SDK", e);
        }
    }
}
