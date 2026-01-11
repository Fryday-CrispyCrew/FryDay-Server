package basakan.fryday.domain.report;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AttendanceIcon {
    EXCELLENT("튀김 장인입니다!", 21),
    GOOD("튀김이 많아지고 있어요", 11),
    POOR("튀김을 열심히 튀겨주세요", 0);

    private final String message;
    private final int minDays;

    public static AttendanceIcon fromAttendanceDays(int attendanceDays) {
        if (attendanceDays >= 21) return EXCELLENT;
        if (attendanceDays >= 11) return GOOD;
        return POOR;
    }
}
