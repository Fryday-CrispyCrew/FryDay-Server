package basakan.fryday.domain.todo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@RequiredArgsConstructor
public enum CharacterStatus {

    CASE_A("투두 없음", "1"),

    CASE_B("시작 전", "2"),
    CASE_C("절반 미만", "3"),
    CASE_D("절반 이상", "4"),

    CASE_E("마감 임박", "5"),
    CASE_F("미완료 상태로 마감", "6"),

    CASE_G("모든 투두 완료", "7"),

    CASE_FUTURE("아직 오지 않은 날", null);

    private final String description;
    private final String imageCode;

    public static CharacterStatus determine(int totalCount, int completedCount, LocalDate targetDate, LocalDateTime now) {
        LocalDate today = now.toLocalDate();

        if (targetDate.isAfter(today)) {
            return CASE_FUTURE;
        }

        if (totalCount == 0) {
            return CASE_A;
        }

        if (totalCount == completedCount) {
            return CASE_G;
        }

        if (targetDate.isBefore(today)) {
            return CASE_F;
        }

        if (now.getHour() >= 22) {
            return CASE_E;
        }

        if (completedCount == 0) {
            return CASE_B;
        }

        double completionRate = (double) completedCount / totalCount * 100;

        if (completionRate < 50) {
            return CASE_C;
        } else {
            return CASE_D;
        }
    }
}