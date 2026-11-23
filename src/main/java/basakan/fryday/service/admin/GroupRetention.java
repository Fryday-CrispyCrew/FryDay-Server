package basakan.fryday.service.admin;

import basakan.fryday.controller.admin.response.GroupRetentionResponse.Comparison;
import basakan.fryday.controller.admin.response.GroupRetentionResponse.Segment;
import basakan.fryday.controller.admin.response.GroupRetentionResponse.Snapshot;
import basakan.fryday.controller.admin.response.RetentionResponse.RetentionDay;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Day 0 무렵 앱을 쓰던 사람을 그날의 그룹 소속으로 나눠 이후 재방문을 비교한다.
 * 소속을 측정보다 먼저 정해야, 오래 남은 사람이 나중에 그룹에 들어간 경우가 그룹 쪽 리텐션을 부풀리지 않는다.
 * 원래 자주 쓰던 사람이 그룹에도 들어가기 쉬워서, 출발선을 비교하도록 Day 0 전 활동일 수를 함께 낸다.
 */
final class GroupRetention {

    static final int ACTIVE_WINDOW_DAYS = 7;

    private GroupRetention() {
    }

    /**
     * 대상은 Day 0 전에 가입을 완료했고 Day 0 까지 7일 중 하루라도 앱을 쓴 사람이다.
     *
     * @param onboardedOnByPerson 사람별 가입 완료일
     * @param activeDates         사람별 활동일. 활동 확인 기간과 측정할 날을 모두 담는다
     * @param groupPeople         Day 0 이 끝날 때 2명 이상 그룹에 속한 사람
     */
    static Split calculate(LocalDate day0, Map<String, LocalDate> onboardedOnByPerson,
                           Map<String, Set<LocalDate>> activeDates, Set<String> groupPeople,
                           LocalDate measurableFrom, LocalDate measurableTo) {
        LocalDate activeFrom = day0.minusDays(ACTIVE_WINDOW_DAYS - 1);
        Map<String, Integer> activeDaysByPerson = new HashMap<>();
        onboardedOnByPerson.forEach((person, onboardedOn) -> {
            if (!onboardedOn.isBefore(day0)) {
                return;
            }
            int activeDays = (int) activeDates.getOrDefault(person, Set.of()).stream()
                    .filter(date -> !date.isBefore(activeFrom) && !date.isAfter(day0))
                    .count();
            if (activeDays > 0) {
                activeDaysByPerson.put(person, activeDays);
            }
        });

        Map<String, LocalDate> group = new HashMap<>();
        Map<String, LocalDate> nonGroup = new HashMap<>();
        activeDaysByPerson.keySet().forEach(person ->
                (groupPeople.contains(person) ? group : nonGroup).put(person, day0));
        return new Split(
                tally(group, activeDaysByPerson, day0, activeDates, measurableFrom, measurableTo),
                tally(nonGroup, activeDaysByPerson, day0, activeDates, measurableFrom, measurableTo));
    }

    static Snapshot snapshot(LocalDate day0, Split split) {
        return Snapshot.builder()
                .day0(day0)
                .group(split.group().toSegment())
                .nonGroup(split.nonGroup().toSegment())
                .build();
    }

    static Comparison total(List<Split> splits) {
        Tally group = Tally.EMPTY;
        Tally nonGroup = Tally.EMPTY;
        for (Split split : splits) {
            group = group.plus(split.group());
            nonGroup = nonGroup.plus(split.nonGroup());
        }
        return Comparison.builder()
                .group(group.toSegment())
                .nonGroup(nonGroup.toSegment())
                .build();
    }

    private static Tally tally(Map<String, LocalDate> day0ByPerson, Map<String, Integer> activeDaysByPerson,
                               LocalDate day0, Map<String, Set<LocalDate>> activeDates,
                               LocalDate measurableFrom, LocalDate measurableTo) {
        int activeDays = day0ByPerson.keySet().stream().mapToInt(activeDaysByPerson::get).sum();
        List<RetentionDay> retention = CohortRetention.calculate(day0ByPerson, activeDates, CohortUnit.DAY,
                day0, day0, measurableFrom, measurableTo).get(0).getRetention();
        return new Tally(day0ByPerson.size(), activeDays, retention);
    }

    record Split(Tally group, Tally nonGroup) {
    }

    // 평균과 비율은 반올림 전 값을 더해야 하므로 합계로 들고 있다
    record Tally(int size, int activeDays, List<RetentionDay> retention) {

        static final Tally EMPTY = new Tally(0, 0, CohortRetention.DAYS.stream()
                .map(day -> CohortRetention.retentionDay(day, 0, 0))
                .toList());

        Tally plus(Tally other) {
            List<RetentionDay> summed = CohortRetention.DAYS.stream()
                    .map(day -> {
                        RetentionDay a = dayOf(retention, day);
                        RetentionDay b = dayOf(other.retention, day);
                        return CohortRetention.retentionDay(day,
                                a.getEligible() + b.getEligible(), a.getReturned() + b.getReturned());
                    })
                    .toList();
            return new Tally(size + other.size, activeDays + other.activeDays, summed);
        }

        Segment toSegment() {
            return Segment.builder()
                    .size(size)
                    .avgActiveDaysBefore(size == 0 ? null : Math.round(activeDays * 10.0 / size) / 10.0)
                    .retention(retention)
                    .build();
        }

        private static RetentionDay dayOf(List<RetentionDay> retention, int day) {
            return retention.stream().filter(point -> point.getDay() == day).findFirst().orElseThrow();
        }
    }
}
