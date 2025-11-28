package basakan.fryday.controller.dto;

import basakan.fryday.domain.Todo;
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

    public TodoListResponse(Todo todo) {
        this.id = todo.getId();
        this.description = todo.getDescription();
        this.status = todo.getStatus().name();
        this.categoryId = todo.getCategory().getId();
        this.displayOrder = todo.getDisplayOrder();
        this.date = todo.getDate();
    }

    public static TodoListResponse from(Todo todo) {
        return new TodoListResponse(todo);
    }
}
