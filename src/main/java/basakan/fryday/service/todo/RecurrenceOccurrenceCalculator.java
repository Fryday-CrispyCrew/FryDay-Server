package basakan.fryday.service.todo;

import basakan.fryday.domain.todo.Recurrence;
import basakan.fryday.domain.todo.RecurrenceType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 반복 규칙에 따라 발생일(occurrence date)을 계산하는 유틸리티 클래스
 */
@Component
public class RecurrenceOccurrenceCalculator {

    private static final int MAX_RANGE_DAYS = 365; // 최대 1년치만 계산

    /**
     * 반복 규칙과 범위를 받아 발생일 목록을 반환합니다.
     * 
     * @param recurrence 반복 규칙
     * @param fromDate 시작 날짜 (포함)
     * @param toDate 종료 날짜 (포함)
     * @param cancelledDates 제외된 날짜 목록 (DELETED, MOVED 예외)
     * @return 발생일 목록 (정렬됨)
     */
    public List<LocalDate> calculateOccurrences(Recurrence recurrence, LocalDate fromDate, LocalDate toDate, Set<LocalDate> cancelledDates) {
        List<LocalDate> occurrences = new ArrayList<>();

        // 범위 제한 체크 (최대 1년치만 계산)
        if (fromDate.plusDays(MAX_RANGE_DAYS).isBefore(toDate)) {
            toDate = fromDate.plusDays(MAX_RANGE_DAYS);
        }

        LocalDate startDate = recurrence.getStartDate().isAfter(fromDate) ? recurrence.getStartDate() : fromDate;
        LocalDate endDate = recurrence.getEndDate();
        if (endDate != null && endDate.isBefore(toDate)) {
            toDate = endDate;
        }

        if (startDate.isAfter(toDate)) {
            return occurrences;
        }

        LocalDate current = startDate;
        while (!current.isAfter(toDate)) {
            if (isMatch(recurrence, current) && !cancelledDates.contains(current)) {
                occurrences.add(current);
            }
            current = current.plusDays(1);
        }

        return occurrences;
    }

    /**
     * 특정 날짜가 반복 규칙에 맞는지 확인합니다.
     */
    private boolean isMatch(Recurrence recurrence, LocalDate date) {
        RecurrenceType type = recurrence.getType();
        String frequencyValuesStr = recurrence.getFrequencyValues();

        List<String> values = (frequencyValuesStr != null && !frequencyValuesStr.isEmpty())
                ? Arrays.asList(frequencyValuesStr.split(","))
                : null;

        switch (type) {
            case DAILY:
                return true;

            case WEEKLY:
                if (values == null || values.isEmpty()) {
                    return false;
                }
                return values.contains(date.getDayOfWeek().name());

            case MONTHLY:
                if (values == null || values.isEmpty()) {
                    return false;
                }
                return values.contains(String.valueOf(date.getDayOfMonth()));

            case YEARLY:
                if (values == null || values.isEmpty()) {
                    return false;
                }
                String monthDay = date.format(DateTimeFormatter.ofPattern("MM-dd"));
                return values.contains(monthDay);

            default:
                return false;
        }
    }
}