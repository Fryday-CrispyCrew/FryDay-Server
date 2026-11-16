package basakan.fryday.service.admin;

import basakan.fryday.controller.admin.response.RetentionResponse.Cohort;
import basakan.fryday.controller.admin.response.RetentionResponse.RetentionDay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DisplayName("기존 사용자 리텐션 계산")
class ExistingUserRetentionTest {

    private static final LocalDate MEASURABLE_FROM = LocalDate.of(2026, 10, 18);
    private static final LocalDate YESTERDAY = LocalDate.of(2026, 11, 15);

    @Test
    @DisplayName("11/11 전에 가입을 완료하고 11/05 ~ 11/11 에 활동한 사람만 기존 사용자로 보고, Day 0 을 11/11 로 센다")
    void selectsExistingUsers() {
        // given
        Map<String, LocalDate> onboardedOn = Map.of(
                "returned", LocalDate.of(2026, 10, 20),
                "lastDay", LocalDate.of(2026, 11, 10),
                "inactive", LocalDate.of(2026, 10, 20),
                "newUser", LocalDate.of(2026, 11, 11));
        Map<String, Set<LocalDate>> active = Map.of(
                "returned", Set.of(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 12)),
                "lastDay", Set.of(LocalDate.of(2026, 11, 11)),
                "inactive", Set.of(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 12)),
                "newUser", Set.of(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 12)));

        // when
        Cohort cohort = ExistingUserRetention.calculate(onboardedOn, active, MEASURABLE_FROM, YESTERDAY);

        // then
        assertThat(cohort.getCohortStart()).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(cohort.getSize()).isEqualTo(2);
        assertThat(cohort.getRetention())
                .extracting(RetentionDay::getDay, RetentionDay::getEligible, RetentionDay::getReturned,
                        RetentionDay::getRate)
                .containsExactly(
                        tuple(1, 2, 1, 50.0),
                        tuple(7, 0, 0, null),
                        tuple(30, 0, 0, null));
    }

    @Test
    @DisplayName("N일째 날이 지나면 기존 사용자 전원이 측정 대상이 된다")
    void allEligibleAfterDay() {
        // given
        Map<String, LocalDate> onboardedOn = Map.of("a", LocalDate.of(2026, 10, 20));
        Map<String, Set<LocalDate>> active = Map.of("a", Set.of(LocalDate.of(2026, 11, 6), LocalDate.of(2026, 12, 11)));

        // when
        Cohort cohort = ExistingUserRetention.calculate(onboardedOn, active, MEASURABLE_FROM, LocalDate.of(2026, 12, 31));

        // then
        assertThat(cohort.getRetention())
                .extracting(RetentionDay::getDay, RetentionDay::getEligible, RetentionDay::getReturned)
                .containsExactly(tuple(1, 1, 0), tuple(7, 1, 0), tuple(30, 1, 1));
    }
}
