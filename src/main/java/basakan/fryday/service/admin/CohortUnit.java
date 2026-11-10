package basakan.fryday.service.admin;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

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
}
