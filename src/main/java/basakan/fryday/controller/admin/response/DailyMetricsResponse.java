package basakan.fryday.controller.admin.response;

import lombok.Builder;
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
    @Builder
    public static class DailyMetric {

        private final LocalDate date;
        private final int activeUsers;
        private final double dauAvg7;
        private final int mau;
        private final int completedUsers;
        private final int signUps;
        private final int cumulativeSignUps;
        private final int cumulativeOnboarded;
    }
}
