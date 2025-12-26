package basakan.fryday.common.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

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

    @Value("${fcm.enabled:false}")
    private boolean fcmEnabled;

    @Value("${fcm.credentials.path:firebase-adminsdk.json}")
    private String credentialsPath;

    @PostConstruct
    public void initialize() {
        if (!fcmEnabled) {
            log.warn("FCM is disabled. Push notifications will not be sent.");
            return;
        }

        try {
            ClassPathResource resource = new ClassPathResource(credentialsPath);

            if (!resource.exists()) {
                log.error("FCM credentials file not found: {}", credentialsPath);
                log.error("Push notifications will not work. Please add {} to src/main/resources/", credentialsPath);
                return;
            }

            InputStream serviceAccount = resource.getInputStream();

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
