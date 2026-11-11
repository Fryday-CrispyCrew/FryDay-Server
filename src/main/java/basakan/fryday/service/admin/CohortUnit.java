package basakan.fryday.service.admin;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public enum CohortUnit {

    DAY {
        @Override
        public LocalDate start(LocalDate date) {
            return date;
        }

        @Override
        public LocalDate next(LocalDate start) {
            return start.plusDays(1);
        }
    },
    // 월요일 시작
    WEEK {
        @Override
        public LocalDate start(LocalDate date) {
            return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }

        @Override
        public LocalDate next(LocalDate start) {
            return start.plusWeeks(1);
        }
    },
    MONTH {
        @Override
        public LocalDate start(LocalDate date) {
            return date.withDayOfMonth(1);
        }

        @Override
        public LocalDate next(LocalDate start) {
            return start.plusMonths(1);
        }
    };

    public abstract LocalDate start(LocalDate date);

    public abstract LocalDate next(LocalDate start);

    public LocalDate end(LocalDate start) {
        return next(start).minusDays(1);
    }

    // 코호트 시작일 순서로, from 과 to 가 속한 코호트는 비어 있어도 넣는다
    Map<LocalDate, List<String>> members(Map<String, LocalDate> dateByPerson, LocalDate from, LocalDate to) {
        LocalDate lastStart = start(to);
        Map<LocalDate, List<String>> members = new LinkedHashMap<>();
        for (LocalDate start = start(from); !start.isAfter(lastStart); start = next(start)) {
            members.put(start, new ArrayList<>());
        }
        dateByPerson.forEach((person, date) -> {
            List<String> cohort = members.get(start(date));
            if (cohort != null) {
                cohort.add(person);
            }
        });
        return members;
    }
}
