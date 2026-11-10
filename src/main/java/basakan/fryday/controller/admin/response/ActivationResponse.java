package basakan.fryday.controller.admin.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class ActivationResponse {

    // from 부터 to 까지 투두를 만든(완료한) 사용자 수
    private final int todoCreatedUsers;
    private final int todoCompletedUsers;
    private final List<Cohort> cohorts;

    @Getter
    @Builder
    public static class Cohort {

        private final LocalDate cohortStart;
        private final LocalDate cohortEnd;
        private final int size;
        private final int eligible;
        // 가입 완료 후 24시간 안에 투두를 만든 사람
        private final int activated;
        // 측정 대상이 없으면 null
        private final Double rate;
    }
}
