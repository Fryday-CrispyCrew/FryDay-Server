package basakan.fryday.controller.admin.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
public class RetentionResponse {

    private final List<Cohort> cohorts;

    public RetentionResponse(List<Cohort> cohorts) {
        this.cohorts = cohorts;
    }

    @Getter
    @Builder
    public static class Cohort {

        private final LocalDate cohortStart;
        private final LocalDate cohortEnd;
        private final int size;
        private final List<RetentionDay> retention;
    }

    @Getter
    @Builder
    public static class RetentionDay {

        private final int day;
        private final int eligible;
        private final int returned;
        // 측정 대상이 없으면 null
        private final Double rate;
    }
}
