package basakan.fryday.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;

@Getter
@RequiredArgsConstructor
public enum BowlType {

    EMPTY("EMPTY", "빈 그릇"),
    COOKING("COOKING", "국자"),
    LESS("LESS", "적은 그릇"),
    MORE("MORE", "많은 그릇"),
    FULL("FULL", "꽉 찬 그릇"),
    BURNT("BURNT", "타버린 그릇");

    private final String code;
    private final String description;

    public static BowlType calculate(int total, int completed, LocalDate targetDate, LocalDate today) {
        // 미래 날짜는 아직 그릇을 보여주지 않는다 (투두 유무와 무관)
        if (targetDate.isAfter(today)) {
            return null;
        }

        if (total == 0) {
            return EMPTY;
        }

        if (completed == total) {
            return FULL;
        }

        // 자정이 지나지 않은 당일은 진행 중이므로 국자로 표시
        if (targetDate.equals(today)) {
            return COOKING;
        }

        // 자정이 지난 지난날(마감)
        if (completed == 0) {
            return BURNT;
        }

        double ratio = (double) completed / total;

        if (ratio < 0.5) {
            return LESS;
        } else {
            return MORE;
        }
    }

}
