package basakan.fryday.common.service.push;

import basakan.fryday.domain.user.User;

import java.util.Map;

/**
 * 푸시 알림 발송 인터페이스
 * - 구현체: FcmPushService (FCM Admin SDK 사용)
 */
public interface PushService {

    String TYPE_KEY = "type";
    String PUSH_ID_KEY = "pushId";
    String TODO_ID_KEY = "todoId";
    String GROUP_ID_KEY = "groupId";

    // data 의 type 이 따라야 하는 형식. FCM analytics label 로도 쓰이므로 그 형식과 같다
    String PUSH_TYPE_REGEX = "[a-zA-Z0-9\\-_.~%]{1,50}";

    /**
     * 특정 사용자의 기기들에 푸시 알림 발송.
     * data 에 발송마다 새 pushId 를 붙이고, 한 대 이상 보내지면 유저당 한 건 PUSH_SENT 이벤트를 남긴다.
     *
     * @param user  알림 수신 사용자
     * @param title 알림 제목
     * @param body  알림 내용
     * @param data  FCM data 페이로드 (키와 값 모두 문자열). type 이 없으면 발송 기록에 알림 종류가 남지 않는다
     */
    void sendToUser(User user, String title, String body, Map<String, String> data);

    /**
     * 특정 FCM 토큰으로 푸시 알림 발송. 발송 기록(PUSH_SENT)은 남기지 않는다
     *
     * @param fcmToken FCM 토큰
     * @param title    알림 제목
     * @param body     알림 내용
     * @param data     FCM data 페이로드 (키와 값 모두 문자열)
     */
    void sendToToken(String fcmToken, String title, String body, Map<String, String> data);
}
