package basakan.fryday.domain.todo;

public enum EndType {
    NONE,   // 무한 반복
    UNTIL,  // 특정 날짜까지 (endDate 사용)
    COUNT   // N회 후 종료 (endCount 사용)
}
