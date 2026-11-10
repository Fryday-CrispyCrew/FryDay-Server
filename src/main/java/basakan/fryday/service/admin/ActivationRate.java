package basakan.fryday.service.admin;

import basakan.fryday.controller.admin.response.ActivationResponse.Cohort;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 가입 완료 시각부터 24시간이 끝나기 전에 첫 투두를 만들었는지 본다. 가입 과정 중에 만든 투두도 센다.
 * 24시간이 아직 다 지나지 않았거나 투두 생성 기록 전에 시작한 사람은 측정 대상에서 뺀다.
 */
final class ActivationRate {

    static final Duration WINDOW = Duration.ofHours(24);

    private ActivationRate() {
    }

    /**
     * @param measurableFrom 투두 생성 기록이 시작된 시각. 기록이 없으면 null 이고 아무것도 측정할 수 없다
     */
    static List<Cohort> calculate(Map<String, LocalDateTime> onboardedAtByPerson,
                                  Map<String, LocalDateTime> firstTodoAt,
                                  CohortUnit unit, LocalDate from, LocalDate to,
                                  LocalDateTime measurableFrom, LocalDateTime now) {
        Map<String, LocalDate> dateByPerson = new HashMap<>();
        onboardedAtByPerson.forEach((person, onboardedAt) -> dateByPerson.put(person, onboardedAt.toLocalDate()));

        List<Cohort> cohorts = new ArrayList<>();
        unit.members(dateByPerson, from, to).forEach((start, people) -> {
            int eligible = 0;
            int activated = 0;
            for (String person : people) {
                LocalDateTime windowStart = onboardedAtByPerson.get(person);
                LocalDateTime windowEnd = windowStart.plus(WINDOW);
                if (measurableFrom == null || windowStart.isBefore(measurableFrom) || windowEnd.isAfter(now)) {
                    continue;
                }
                eligible++;
                LocalDateTime firstTodo = firstTodoAt.get(person);
                if (firstTodo != null && firstTodo.isBefore(windowEnd)) {
                    activated++;
                }
            }
            cohorts.add(Cohort.builder()
                    .cohortStart(start)
                    .cohortEnd(unit.end(start))
                    .size(people.size())
                    .eligible(eligible)
                    .activated(activated)
                    .rate(eligible == 0 ? null : Math.round(activated * 1000.0 / eligible) / 10.0)
                    .build());
        });
        return cohorts;
    }
}
