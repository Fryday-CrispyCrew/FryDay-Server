package basakan.fryday.controller.admin.response;

import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
public class WeeklyMetricsResponse {

    private final List<WeeklyMetric> weeks;

    public WeeklyMetricsResponse(List<WeeklyMetric> weeks) {
        this.weeks = weeks;
    }

    @Getter
    public static class WeeklyMetric {

        private final LocalDate weekStart;
        private final LocalDate weekEnd;
        private final int activeUsers;
        private final int completedUsers;
        private final int signUps;

        public WeeklyMetric(LocalDate weekStart, LocalDate weekEnd, int activeUsers, int completedUsers, int signUps) {
            this.weekStart = weekStart;
            this.weekEnd = weekEnd;
            this.activeUsers = activeUsers;
            this.completedUsers = completedUsers;
            this.signUps = signUps;
        }
    }
}
