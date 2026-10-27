package basakan.fryday.domain.eventlog;

public enum EventType {
    USER_SIGNED_UP,
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
    GROUP_NOTIFICATION_TOGGLED
}
