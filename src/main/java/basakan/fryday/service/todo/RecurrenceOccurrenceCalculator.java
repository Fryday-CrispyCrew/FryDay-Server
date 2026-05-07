package basakan.fryday.service.todo;

import basakan.fryday.domain.todo.EndType;
import basakan.fryday.domain.todo.Recurrence;
import basakan.fryday.domain.todo.RecurrenceType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class RecurrenceOccurrenceCalculator {

    private static final int MAX_RANGE_DAYS = 365;

    public List<LocalDate> calculateOccurrences(Recurrence recurrence, LocalDate fromDate, LocalDate toDate) {
        List<LocalDate> occurrences = new ArrayList<>();

        if (fromDate.plusDays(MAX_RANGE_DAYS).isBefore(toDate)) {
            toDate = fromDate.plusDays(MAX_RANGE_DAYS);
        }

        LocalDate startDate = recurrence.getStartDate().isAfter(fromDate) ? recurrence.getStartDate() : fromDate;

        if (recurrence.getEndType() == EndType.UNTIL && recurrence.getEndDate() != null) {
            if (recurrence.getEndDate().isBefore(toDate)) {
                toDate = recurrence.getEndDate();
            }
        }

        if (startDate.isAfter(toDate)) {
            return occurrences;
        }

        LocalDate current = startDate;

        while (!current.isAfter(toDate)) {
            if (isMatch(recurrence, current)) {
                occurrences.add(current);
            }
            current = current.plusDays(1);
        }

        return occurrences;
    }

    public boolean isMatch(Recurrence recurrence, LocalDate date) {
        RecurrenceType type = recurrence.getType();
        String frequencyValuesStr = recurrence.getFrequencyValues();

        List<String> values = (frequencyValuesStr != null && !frequencyValuesStr.isEmpty())
                ? Arrays.asList(frequencyValuesStr.split(","))
                : null;

        return switch (type) {
            case DAILY -> true;
            case WEEKLY -> values != null && values.contains(date.getDayOfWeek().name());
            case MONTHLY -> values != null && values.contains(String.valueOf(date.getDayOfMonth()));
            case YEARLY -> {
                String monthDay = date.format(DateTimeFormatter.ofPattern("MM-dd"));
                yield values != null && values.contains(monthDay);
            }
        };
    }
}
