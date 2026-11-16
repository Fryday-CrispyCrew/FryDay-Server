package basakan.fryday.service.admin;

import basakan.fryday.controller.admin.response.RetentionResponse.Cohort;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 가입 시점이 제각각인 기존 사용자를 기준일 하나로 묶어 리텐션을 본다.
 * 기준일 전에 가입을 완료했고, 기준일까지 7일 중 하루라도 앱을 쓴 사람이 대상이다.
 */
final class ExistingUserRetention {

    static final LocalDate DAY0 = LocalDate.of(2026, 11, 11);
    static final LocalDate ACTIVE_FROM = DAY0.minusDays(6);

    private ExistingUserRetention() {
    }

    /**
     * @param onboardedOnByPerson 사람별 가입 완료일
     * @param activeDates         사람별 활동일. 활동 확인 기간과 측정할 날을 모두 담는다
     */
    static Cohort calculate(Map<String, LocalDate> onboardedOnByPerson, Map<String, Set<LocalDate>> activeDates,
                            LocalDate measurableFrom, LocalDate measurableTo) {
        Map<String, LocalDate> day0ByPerson = new HashMap<>();
        onboardedOnByPerson.forEach((person, onboardedOn) -> {
            if (onboardedOn.isBefore(DAY0) && activeBeforeDay0(activeDates.getOrDefault(person, Set.of()))) {
                day0ByPerson.put(person, DAY0);
            }
        });
        return CohortRetention.calculate(day0ByPerson, activeDates, CohortUnit.DAY, DAY0, DAY0,
                measurableFrom, measurableTo).get(0);
    }

    private static boolean activeBeforeDay0(Set<LocalDate> dates) {
        return dates.stream().anyMatch(date -> !date.isBefore(ACTIVE_FROM) && !date.isAfter(DAY0));
    }
}
