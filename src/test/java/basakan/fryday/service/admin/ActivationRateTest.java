package basakan.fryday.service.admin;

import basakan.fryday.controller.admin.response.ActivationResponse.Cohort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DisplayName("24시간 내 첫 투두 생성률 계산")
class ActivationRateTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);
    private static final LocalDateTime TRACKED_SINCE = LocalDateTime.of(2026, 10, 17, 15, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 30, 12, 0);

    @Test
    @DisplayName("가입 완료 후 24시간이 끝나기 전에 만든 첫 투두를 센다. 가입 과정 중에 만든 투두도 센다")
    void countsFirstTodoBeforeWindowEnds() {
        // given
        LocalDateTime onboardedAt = DAY.atTime(20, 0);
        Map<String, LocalDateTime> onboarded = Map.of(
                "in", onboardedAt, "duringSignUp", onboardedAt, "edge", onboardedAt, "late", onboardedAt);
        Map<String, LocalDateTime> created = Map.of(
                "in", DAY.plusDays(1).atTime(19, 59),
                "duringSignUp", DAY.atTime(19, 50),
                "edge", DAY.plusDays(1).atTime(20, 0),
                "late", DAY.plusDays(2).atTime(9, 0));

        // when
        List<Cohort> cohorts = ActivationRate.calculate(onboarded, created, CohortUnit.DAY, DAY, DAY, TRACKED_SINCE, NOW);

        // then
        assertThat(cohorts).extracting(Cohort::getSize, Cohort::getEligible, Cohort::getActivated, Cohort::getRate)
                .containsExactly(tuple(4, 4, 2, 50.0));
    }

    @Test
    @DisplayName("24시간이 아직 지나지 않았거나 투두 생성 기록 전에 가입한 사람은 측정 대상에서 빼고, 대상이 없으면 비율은 null")
    void excludesUnmeasurable() {
        // given
        Map<String, LocalDateTime> onboarded = Map.of(
                "beforeTracking", LocalDateTime.of(2026, 10, 17, 9, 0),
                "afterTracking", LocalDateTime.of(2026, 10, 17, 16, 0),
                "recent", NOW.minusHours(23));
        Map<String, LocalDateTime> created = Map.of(
                "beforeTracking", LocalDateTime.of(2026, 10, 17, 16, 0),
                "recent", NOW.minusHours(1));

        // when
        List<Cohort> cohorts = ActivationRate.calculate(onboarded, created, CohortUnit.MONTH,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30), TRACKED_SINCE, NOW);

        // then
        assertThat(cohorts).extracting(Cohort::getCohortStart, Cohort::getSize, Cohort::getEligible,
                        Cohort::getActivated, Cohort::getRate)
                .containsExactly(
                        tuple(LocalDate.of(2026, 10, 1), 2, 1, 0, 0.0),
                        tuple(LocalDate.of(2026, 11, 1), 1, 0, 0, null));
    }

    @Test
    @DisplayName("투두 생성 기록이 없으면 아무것도 측정하지 않는다")
    void nothingMeasurableWithoutEvents() {
        // when
        List<Cohort> cohorts = ActivationRate.calculate(Map.of("a", DAY.atTime(9, 0)), Map.of(),
                CohortUnit.DAY, DAY, DAY, null, NOW);

        // then
        assertThat(cohorts).extracting(Cohort::getEligible, Cohort::getRate).containsExactly(tuple(0, null));
    }
}
