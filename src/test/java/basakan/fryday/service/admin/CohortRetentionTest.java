package basakan.fryday.service.admin;

import basakan.fryday.controller.admin.response.RetentionResponse.Cohort;
import basakan.fryday.controller.admin.response.RetentionResponse.RetentionDay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DisplayName("코호트 리텐션 계산")
class CohortRetentionTest {

    private static final LocalDate DAY0 = LocalDate.of(2026, 10, 20);
    private static final LocalDate TRACKED_SINCE = LocalDate.of(2026, 10, 17);
    private static final LocalDate YESTERDAY = LocalDate.of(2026, 12, 31);

    @Test
    @DisplayName("DN 은 Day 0 으로부터 정확히 N일째 날의 활동만 재방문으로 센다")
    void countsOnlyExactDay() {
        // given
        Map<String, LocalDate> day0 = Map.of("a", DAY0, "b", DAY0);
        Map<String, Set<LocalDate>> active = Map.of(
                "a", Set.of(DAY0.plusDays(1), DAY0.plusDays(7)),
                "b", Set.of(DAY0.plusDays(6), DAY0.plusDays(8), DAY0.plusDays(29)));

        // when
        List<Cohort> cohorts = calculate(day0, active, CohortUnit.DAY, DAY0, DAY0, TRACKED_SINCE, YESTERDAY);

        // then
        assertThat(cohorts).singleElement().extracting(Cohort::getSize).isEqualTo(2);
        assertThat(cohorts.get(0).getRetention())
                .extracting(RetentionDay::getDay, RetentionDay::getEligible, RetentionDay::getReturned,
                        RetentionDay::getRate)
                .containsExactly(
                        tuple(1, 2, 1, 50.0),
                        tuple(7, 2, 1, 50.0),
                        tuple(30, 2, 0, 0.0));
    }

    @Test
    @DisplayName("N일째 날이 어제보다 뒤거나 활동 기록 전이면 측정 대상에서 빼고, 대상이 없으면 비율은 null")
    void excludesUnmeasurableDays() {
        // given
        Map<String, LocalDate> day0 = Map.of(
                "early", LocalDate.of(2026, 10, 10),  // D1 은 수집 전, D7 은 수집 첫날
                "recent", LocalDate.of(2026, 12, 28)); // D7, D30 은 아직 오지 않음
        Map<String, Set<LocalDate>> active = Map.of(
                "early", Set.of(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 17)),
                "recent", Set.of(LocalDate.of(2026, 12, 29)));

        // when
        List<Cohort> cohorts = calculate(day0, active, CohortUnit.MONTH,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), TRACKED_SINCE, YESTERDAY);

        // then
        assertThat(cohorts).extracting(Cohort::getCohortStart, Cohort::getCohortEnd, Cohort::getSize)
                .containsExactly(
                        tuple(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 1),
                        tuple(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), 0),
                        tuple(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31), 1));
        assertThat(cohorts.get(0).getRetention())
                .extracting(RetentionDay::getDay, RetentionDay::getEligible, RetentionDay::getReturned,
                        RetentionDay::getRate)
                .containsExactly(tuple(1, 0, 0, null), tuple(7, 1, 1, 100.0), tuple(30, 1, 0, 0.0));
        assertThat(cohorts.get(1).getRetention()).extracting(RetentionDay::getRate).containsOnlyNulls();
        assertThat(cohorts.get(2).getRetention())
                .extracting(RetentionDay::getDay, RetentionDay::getEligible, RetentionDay::getRate)
                .containsExactly(tuple(1, 1, 100.0), tuple(7, 0, null), tuple(30, 0, null));
    }

    @Test
    @DisplayName("활동 기록이 하나도 없으면 아무것도 측정하지 않는다")
    void nothingMeasurableWithoutActivity() {
        // when
        List<Cohort> cohorts = calculate(Map.of("a", DAY0), Map.of(), CohortUnit.DAY, DAY0, DAY0, null, YESTERDAY);

        // then
        assertThat(cohorts.get(0).getRetention()).extracting(RetentionDay::getEligible).containsOnly(0);
    }

    @Test
    @DisplayName("주 코호트는 월요일 시작이고, from 과 to 가 속한 주 전체의 가입자를 넣는다")
    void weeklyCohortsSpanWholeWeeks() {
        // given (10/21 수 ~ 10/27 화 → 10/19 월 ~ 11/1 일 두 주)
        Map<String, LocalDate> day0 = Map.of(
                "mon", LocalDate.of(2026, 10, 19),
                "sun", LocalDate.of(2026, 11, 1),
                "before", LocalDate.of(2026, 10, 18),
                "after", LocalDate.of(2026, 11, 2));

        // when
        List<Cohort> cohorts = calculate(day0, Map.of(), CohortUnit.WEEK,
                LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 27), TRACKED_SINCE, YESTERDAY);

        // then
        assertThat(cohorts).extracting(Cohort::getCohortStart, Cohort::getCohortEnd, Cohort::getSize)
                .containsExactly(
                        tuple(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25), 1),
                        tuple(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1), 1));
    }

    private List<Cohort> calculate(Map<String, LocalDate> day0, Map<String, Set<LocalDate>> active, CohortUnit unit,
                                   LocalDate from, LocalDate to, LocalDate measurableFrom, LocalDate measurableTo) {
        return CohortRetention.calculate(day0, active, unit, from, to, measurableFrom, measurableTo);
    }
}
