package basakan.fryday.controller.admin.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class MetricsSummaryResponse {

    private final LocalDate date;
    private final int cumulativeSignUps;
    private final int cumulativeOnboarded;
    private final int dau;
    private final double dauAvg7;
    private final int mau;
    private final double dauMauRate;
    private final int weeklyCompletedUsers;
    private final LocalDate activityTrackedSince;
}
