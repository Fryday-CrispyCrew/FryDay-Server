package basakan.fryday.service.admin;

import basakan.fryday.controller.admin.response.RetentionResponse.Cohort;
import basakan.fryday.controller.admin.response.RetentionResponse.RetentionDay;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DN 재방문은 Day 0 으로부터 정확히 N일째 날의 활동만 본다.
 * N일째 날이 아직 지나지 않았거나 활동 기록이 없는 기간이면 측정 대상에서 뺀다. 0% 로 세면 리텐션이 실제보다 낮게 나온다.
 */
final class CohortRetention {

    static final List<Integer> DAYS = List.of(1, 7, 30);
    static final int LONGEST_DAY = 30;

    private CohortRetention() {
    }

    /**
     * @param measurableFrom 활동 기록이 하루 내내 쌓인 첫날. 기록이 없으면 null 이고 아무것도 측정할 수 없다
     * @param measurableTo   하루가 다 끝난 마지막 날 (어제)
     */
    static List<Cohort> calculate(Map<String, LocalDate> day0ByPerson, Map<String, Set<LocalDate>> activeDates,
                                  CohortUnit unit, LocalDate from, LocalDate to,
                                  LocalDate measurableFrom, LocalDate measurableTo) {
        LocalDate firstStart = unit.start(from);
        LocalDate lastStart = unit.start(to);

        Map<LocalDate, List<String>> members = new HashMap<>();
        day0ByPerson.forEach((person, day0) -> {
            LocalDate start = unit.start(day0);
            if (!start.isBefore(firstStart) && !start.isAfter(lastStart)) {
                members.computeIfAbsent(start, key -> new ArrayList<>()).add(person);
            }
        });

        List<Cohort> cohorts = new ArrayList<>();
        for (LocalDate start = firstStart; !start.isAfter(lastStart); start = unit.next(start)) {
            List<String> people = members.getOrDefault(start, List.of());
            List<RetentionDay> retention = DAYS.stream()
                    .map(day -> retentionDay(day, people, day0ByPerson, activeDates, measurableFrom, measurableTo))
                    .toList();
            cohorts.add(Cohort.builder()
                    .cohortStart(start)
                    .cohortEnd(unit.end(start))
                    .size(people.size())
                    .retention(retention)
                    .build());
        }
        return cohorts;
    }

    private static RetentionDay retentionDay(int day, List<String> people, Map<String, LocalDate> day0ByPerson,
                                             Map<String, Set<LocalDate>> activeDates,
                                             LocalDate measurableFrom, LocalDate measurableTo) {
        int eligible = 0;
        int returned = 0;
        for (String person : people) {
            LocalDate target = day0ByPerson.get(person).plusDays(day);
            if (measurableFrom == null || target.isBefore(measurableFrom) || target.isAfter(measurableTo)) {
                continue;
            }
            eligible++;
            if (activeDates.getOrDefault(person, Set.of()).contains(target)) {
                returned++;
            }
        }
        return RetentionDay.builder()
                .day(day)
                .eligible(eligible)
                .returned(returned)
                .rate(eligible == 0 ? null : Math.round(returned * 1000.0 / eligible) / 10.0)
                .build();
    }
}
