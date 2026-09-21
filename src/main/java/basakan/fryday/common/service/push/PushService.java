package basakan.fryday.common.service.push;

import basakan.fryday.domain.user.User;

import java.util.Map;

/**
 * 푸시 알림 발송 인터페이스
 * - 구현체: FcmPushService (FCM Admin SDK 사용)
 */
public interface PushService {

    /**
     * 특정 사용자에게 푸시 알림 발송
     *
     * @param user  알림 수신 사용자
     * @param title 알림 제목
     * @param body  알림 내용
     */
    void sendToUser(User user, String title, String body);

    /**
     * 특정 사용자에게 앱이 화면 이동에 쓸 데이터를 함께 담아 푸시 알림 발송
     *
     * @param user  알림 수신 사용자
     * @param title 알림 제목
     * @param body  알림 내용
     * @param data  FCM data 페이로드 (키와 값 모두 문자열)
     */
    void sendToUser(User user, String title, String body, Map<String, String> data);

    /**
     * 특정 FCM 토큰으로 푸시 알림 발송
     *
     * @param fcmToken FCM 토큰
     * @param title    알림 제목
     * @param body     알림 내용
     */
    void sendToToken(String fcmToken, String title, String body);
}
