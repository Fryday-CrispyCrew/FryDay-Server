package basakan.fryday.api.dto;

import basakan.fryday.domain.Todo;
import lombok.Getter;

@Getter
public class TodoResponse {

    private final Long id;
    private final String description;
    private final String status;

    public TodoResponse(Todo todo) {
        this.id = todo.getId();
        this.description = todo.getDescription();
        this.status = todo.getStatus().name();
    }

    public static TodoResponse from(Todo todo) {
        return new TodoResponse(todo);
    }
}
