package basakan.fryday.domain.report;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AttendanceIcon {
    EXCELLENT("완벽한 한 달이었어요!", 90.0),
    GREAT("잘 하고 있어요!", 70.0),
    GOOD("노력이 보여요!", 50.0),
    NEEDS_IMPROVEMENT("조금만 더 힘내요!", 30.0),
    POOR("다음 달엔 할 수 있어요!", 0.0);

    private final String message;
    private final double minRate;

    public static AttendanceIcon fromAchievementRate(double achievementRate) {
        if (achievementRate >= 90.0) return EXCELLENT;
        if (achievementRate >= 70.0) return GREAT;
        if (achievementRate >= 50.0) return GOOD;
        if (achievementRate >= 30.0) return NEEDS_IMPROVEMENT;
        return POOR;
    }
}
