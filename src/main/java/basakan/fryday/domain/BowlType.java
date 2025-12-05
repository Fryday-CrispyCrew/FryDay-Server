package basakan.fryday.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BowlType {

    EMPTY("EMPTY", "빈 그릇"),
    LESS("LESS", "적은 그릇"),
    MORE("MORE", "많은 그릇"),
    FULL("FULL", "꽉 찬 그릇"),
    BURNT("BURNT", "타버린 그릇");

    private final String code;
    private final String description;

    public static BowlType calculate(int total, int completed) {
        if (total == 0) {
            return EMPTY;
        }

        if (completed == total) {
            return FULL;
        }

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
