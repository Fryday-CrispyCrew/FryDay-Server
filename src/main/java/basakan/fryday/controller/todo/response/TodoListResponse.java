package basakan.fryday.controller.todo.response;

import basakan.fryday.domain.todo.Todo;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class TodoListResponse {

    private final Long id;
    private final String description;
    private final String status;
    private final Long categoryId;
    private final Long displayOrder;
    private final LocalDate date;
    private final Long recurrenceId;  // 반복 투두 ID (일반 투두는 null)
    private final LocalDate occurrenceDate;  // 가상 회차의 발생일 (반복 투두만)
    private final String memo;
    private final boolean isOverridden;  // 이번 인스턴스만 개별 수정된 경우 true

    public TodoListResponse(Todo todo) {
        this.id = todo.getId();
        this.description = todo.isOverridden() && todo.getOverrideTitle() != null
                ? todo.getOverrideTitle() : todo.getDescription();
        this.status = todo.getStatus().name();
        this.categoryId = todo.getCategory().getId();
        this.displayOrder = todo.getDisplayOrder();
        this.date = todo.getDate();
        this.recurrenceId = todo.getRecurrenceId();
        // 반복 투두의 경우 date가 occurrenceDate와 같음 (생성 시 occurrenceDate로 설정됨)
        this.occurrenceDate = todo.getRecurrenceId() != null ? todo.getDate() : null;
        this.memo = todo.isOverridden() && todo.getOverrideMemo() != null
                ? todo.getOverrideMemo() : todo.getMemo();
        this.isOverridden = todo.isOverridden();
    }

    // 가상 회차(반복 투두)용 생성자
    public TodoListResponse(Long recurrenceId, String description, String status, Long categoryId, 
                           Long displayOrder, LocalDate occurrenceDate, String memo) {
        this.id = null;  // 가상 회차는 DB id가 없음
        this.description = description;
        this.status = status;
        this.categoryId = categoryId;
        this.displayOrder = displayOrder;
        this.date = occurrenceDate;
        this.recurrenceId = recurrenceId;
        this.occurrenceDate = occurrenceDate;
        this.memo = memo;
        this.isOverridden = false;
    }

    public static TodoListResponse from(Todo todo) {
        return new TodoListResponse(todo);
    }

    public static TodoListResponse fromVirtualOccurrence(Long recurrenceId, String description,
                                                        String status, Long categoryId,
                                                        Long displayOrder, LocalDate occurrenceDate, String memo) {
        return new TodoListResponse(recurrenceId, description, status, categoryId, displayOrder, occurrenceDate, memo);
    }
}
