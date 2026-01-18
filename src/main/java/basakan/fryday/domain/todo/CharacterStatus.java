package basakan.fryday.domain.todo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@RequiredArgsConstructor
public enum CharacterStatus {

    CASE_A("투두 없음", "a_graphic"),

    CASE_B("시작 전", "b_graphic"),
    CASE_C("절반 미만", "c_graphic"),
    CASE_D("절반 이상", "d_graphic"),

    CASE_E1("마감 임박", "e1_graphic"),
    CASE_E2("마감 임박", "e2_graphic"),
    CASE_F("미완료 상태로 마감", "f_graphic"),

    CASE_G("모든 투두 완료", "g_graphic");

    private final String description;
    private final String imageCode;

    public static CharacterStatus determine(int totalCount, int completedCount, LocalDate targetDate, LocalDateTime now) {
        LocalDate today = now.toLocalDate();

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
            return Math.random() < 0.5 ? CASE_E1 : CASE_E2;
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