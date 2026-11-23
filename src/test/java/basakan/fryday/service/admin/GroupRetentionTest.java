package basakan.fryday.service.admin;

import basakan.fryday.controller.admin.response.GroupRetentionResponse.Comparison;
import basakan.fryday.controller.admin.response.GroupRetentionResponse.Segment;
import basakan.fryday.controller.admin.response.GroupRetentionResponse.Snapshot;
import basakan.fryday.controller.admin.response.RetentionResponse.RetentionDay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DisplayName("그룹 / 비그룹 리텐션 계산")
class GroupRetentionTest {

    private static final LocalDate DAY0 = LocalDate.of(2026, 11, 9);
    private static final LocalDate MEASURABLE_FROM = LocalDate.of(2026, 10, 18);
    private static final LocalDate YESTERDAY = LocalDate.of(2026, 11, 22);
    private static final LocalDate ONBOARDED = LocalDate.of(2026, 10, 20);

    @Test
    @DisplayName("Day 0 전에 가입을 완료하고 Day 0 까지 7일 안에 활동한 사람만 그룹 소속으로 나눈다")
    void selectsAndSplits() {
        // given
        Map<String, LocalDate> onboardedOn = Map.of(
                "grouped", ONBOARDED,
                "solo", ONBOARDED,
                "inactive", ONBOARDED,
                "newUser", DAY0);
        Map<String, Set<LocalDate>> active = Map.of(
                "grouped", Set.of(DAY0.minusDays(6), DAY0.minusDays(1), DAY0, DAY0.plusDays(1)),
                "solo", Set.of(DAY0, DAY0.plusDays(7)),
                "inactive", Set.of(DAY0.minusDays(7), DAY0.plusDays(1)),
                "newUser", Set.of(DAY0, DAY0.plusDays(1)));

        // when
        GroupRetention.Split split = GroupRetention.calculate(DAY0, onboardedOn, active,
                Set.of("grouped", "inactive", "newUser"), MEASURABLE_FROM, YESTERDAY);
        Snapshot snapshot = GroupRetention.snapshot(DAY0, split);

        // then
        assertThat(snapshot.getDay0()).isEqualTo(DAY0);
        assertSegment(snapshot.getGroup(), 1, 3.0, tuple(1, 1, 1, 100.0), tuple(7, 1, 0, 0.0), tuple(30, 0, 0, null));
        assertSegment(snapshot.getNonGroup(), 1, 1.0, tuple(1, 1, 0, 0.0), tuple(7, 1, 1, 100.0), tuple(30, 0, 0, null));
    }

    @Test
    @DisplayName("합산은 Day 0 별 측정 대상과 재방문 수를 더해 비율을 다시 내고, 활동일 수는 인원 가중 평균이다")
    void total() {
        // given
        LocalDate nextDay0 = DAY0.plusWeeks(1);
        Map<String, LocalDate> onboardedOn = Map.of("a", ONBOARDED, "b", ONBOARDED, "c", ONBOARDED);
        Map<String, Set<LocalDate>> active = Map.of(
                "a", Set.of(DAY0, DAY0.plusDays(1), nextDay0),
                "b", Set.of(DAY0.minusDays(1), DAY0),
                "c", Set.of(nextDay0.minusDays(2), nextDay0, nextDay0.plusDays(1)));
        GroupRetention.Split first = GroupRetention.calculate(DAY0, onboardedOn, active,
                Set.of("a", "b"), MEASURABLE_FROM, YESTERDAY);
        GroupRetention.Split second = GroupRetention.calculate(nextDay0, onboardedOn, active,
                Set.of("a", "b", "c"), MEASURABLE_FROM, YESTERDAY);

        // when
        Comparison total = GroupRetention.total(List.of(first, second));

        // then
        // 11/09: a(1일, D1·D7 재방문), b(2일) / 11/16: a(2일), c(2일, D1 재방문), b 는 활동 없음, D7 은 아직 측정 전
        assertSegment(total.getGroup(), 4, 1.8, tuple(1, 4, 2, 50.0), tuple(7, 2, 1, 50.0), tuple(30, 0, 0, null));
        assertThat(total.getNonGroup().getSize()).isZero();
        assertThat(total.getNonGroup().getAvgActiveDaysBefore()).isNull();
    }

    @Test
    @DisplayName("Day 0 이 없으면 합산은 인원 0, 평균 null, 비율 null 이다")
    void emptyTotal() {
        Comparison total = GroupRetention.total(List.of());

        assertSegment(total.getGroup(), 0, null, tuple(1, 0, 0, null), tuple(7, 0, 0, null), tuple(30, 0, 0, null));
    }

    private void assertSegment(Segment segment, int size, Double avgActiveDays, org.assertj.core.groups.Tuple... days) {
        assertThat(segment.getSize()).isEqualTo(size);
        assertThat(segment.getAvgActiveDaysBefore()).isEqualTo(avgActiveDays);
        assertThat(segment.getRetention())
                .extracting(RetentionDay::getDay, RetentionDay::getEligible, RetentionDay::getReturned,
                        RetentionDay::getRate)
                .containsExactly(days);
    }
}
