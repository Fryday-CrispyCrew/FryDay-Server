package basakan.fryday.controller.todo.response;

import basakan.fryday.domain.todo.Todo;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class TodoResponse {

    private final Long id;
    private final String description;
    private final String status;
    private final Long categoryId;
    private final String memo;
    private final LocalDate date;

    public TodoResponse(Todo todo) {
        this.id = todo.getId();
        this.description = todo.getDescription();
        this.status = todo.getStatus().name();
        this.categoryId = todo.getCategory().getId();
        this.memo = todo.getMemo();
        this.date = todo.getDate();
    }

    public static TodoResponse from(Todo todo) {
        return new TodoResponse(todo);
    }
}
