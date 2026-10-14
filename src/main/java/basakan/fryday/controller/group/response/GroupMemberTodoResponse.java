package basakan.fryday.controller.group.response;

import basakan.fryday.domain.todo.Todo;
import lombok.Getter;

/** 그룹원에게 보여주는 투두. 메모는 개인적인 내용일 수 있어 내려주지 않는다. */
@Getter
public class GroupMemberTodoResponse {

    private final Long todoId;
    private final String description;
    private final String status;
    private final Long displayOrder;
    private final Long recurrenceId;

    private GroupMemberTodoResponse(Todo todo) {
        this.todoId = todo.getId();
        this.description = todo.isOverridden() && todo.getOverrideTitle() != null
                ? todo.getOverrideTitle() : todo.getDescription();
        this.status = todo.getStatus().name();
        this.displayOrder = todo.getDisplayOrder();
        this.recurrenceId = todo.getRecurrenceId();
    }

    public static GroupMemberTodoResponse from(Todo todo) {
        return new GroupMemberTodoResponse(todo);
    }
}
