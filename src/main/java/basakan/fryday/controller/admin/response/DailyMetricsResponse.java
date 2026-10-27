package basakan.fryday.controller.admin.response;

import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
public class DailyMetricsResponse {

    private final List<DailyMetric> days;

    public DailyMetricsResponse(List<DailyMetric> days) {
        this.days = days;
    }

    @Getter
    public static class DailyMetric {

        private final LocalDate date;
        private final int activeUsers;
        private final int completedUsers;
        private final int signUps;

        public DailyMetric(LocalDate date, int activeUsers, int completedUsers, int signUps) {
            this.date = date;
            this.activeUsers = activeUsers;
            this.completedUsers = completedUsers;
            this.signUps = signUps;
        }
    }
}
