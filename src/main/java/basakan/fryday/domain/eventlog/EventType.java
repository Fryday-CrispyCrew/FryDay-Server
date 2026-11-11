package basakan.fryday.domain.eventlog;

public enum EventType {
    USER_SIGNED_UP,
    // 약관, 닉네임, 온보딩 화면까지 마친 시점. 처음 완료할 때 한 번만 남긴다
    ONBOARDING_COMPLETED,
    USER_WITHDREW,

    // 유저가 직접 만든 투두만. 반복 회차가 자동으로 만들어지는 것은 세지 않는다
    TODO_CREATED,
    TODO_COMPLETED,
    TODO_UNCOMPLETED,
    TODO_DELETED,

    RECURRENCE_CREATED,
    // 반복 전체 삭제, 이후 회차 삭제, 반복 해제
    RECURRENCE_ENDED,

    GROUP_CREATED,
    // 초대 코드로 그룹 정보를 확인한 경우. 초대 전환율의 분모다
    INVITE_CODE_PREVIEWED,
    GROUP_JOINED,
    // 직접 나간 경우만. 해체나 탈퇴로 빠진 것은 GROUP_DISBANDED, USER_WITHDREW 로 본다
    GROUP_LEFT,
    GROUP_DISBANDED,
    GROUP_INTERACTION_SENT,
    // 값이 실제로 바뀐 경우만
    GROUP_NOTIFICATION_TOGGLED,

    // 앱이 알려주는 이벤트. 발생 시각은 서버가 요청을 받은 시각이다
    INVITE_SHARED,
    NOTIFICATION_OPENED,

    // 유저 단위. 기기가 여러 대여도 한 건이고, 한 대도 보내지 못하면 남기지 않는다
    PUSH_SENT
}
