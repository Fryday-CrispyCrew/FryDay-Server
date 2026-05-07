package basakan.fryday.controller.todo.response;

import basakan.fryday.domain.todo.Recurrence;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.domain.todo.TodoAlarm;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Builder
public class TodoDetailResponse {

    private final Long id;
    private final String description;
    private final String status;
    private final Long categoryId;
    private final String memo;
    private final LocalDate date;
    private final boolean isOverridden;
    private final Boolean overrideIsAlarm;
    private final LocalTime overrideAlarmTime;

    private final TodoAlarmInfo alarm;
    private final RecurrenceInfo recurrence;

    @Getter
    @Builder
    public static class TodoAlarmInfo {
        private final Long alarmId;
        private final LocalDateTime notifyAt;
        private final String status;

        public static TodoAlarmInfo from(TodoAlarm todoAlarm) {
            if (todoAlarm == null) {
                return null;
            }
            return TodoAlarmInfo.builder()
                    .alarmId(todoAlarm.getId())
                    .notifyAt(todoAlarm.getNotifyAt())
                    .status(todoAlarm.getStatus().name())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class RecurrenceInfo {
        private final Long recurrenceId;
        private final String type;
        private final String frequencyValues;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final LocalTime notificationTime;
        private final boolean isAlarmEnabled;

        public static RecurrenceInfo from(Recurrence recurrence) {
            if (recurrence == null) {
                return null;
            }
            return RecurrenceInfo.builder()
                    .recurrenceId(recurrence.getId())
                    .type(recurrence.getType().name())
                    .frequencyValues(recurrence.getFrequencyValues())
                    .startDate(recurrence.getStartDate())
                    .endDate(recurrence.getEndDate())
                    .notificationTime(recurrence.getNotificationTime())
                    .isAlarmEnabled(recurrence.isAlarmEnabled())
                    .build();
        }
    }

    public static TodoDetailResponse from(Todo todo, TodoAlarm todoAlarm, Recurrence recurrence) {
        return TodoDetailResponse.builder()
                .id(todo.getId())
                .description(todo.isOverridden() && todo.getOverrideTitle() != null
                        ? todo.getOverrideTitle() : todo.getDescription())
                .status(todo.getStatus().name())
                .categoryId(todo.getCategory().getId())
                .memo(todo.isOverridden() && todo.getOverrideMemo() != null
                        ? todo.getOverrideMemo() : todo.getMemo())
                .date(todo.getDate())
                .isOverridden(todo.isOverridden())
                .overrideIsAlarm(todo.isOverridden() ? todo.getOverrideIsAlarm() : null)
                .overrideAlarmTime(todo.isOverridden() ? todo.getOverrideAlarmTime() : null)
                .alarm(TodoAlarmInfo.from(todoAlarm))
                .recurrence(RecurrenceInfo.from(recurrence))
                .build();
    }
}

