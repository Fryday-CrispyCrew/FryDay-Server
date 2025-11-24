package basakan.fryday.controller.dto;

import lombok.Getter;

@Getter
public class MemoResponse {

    private final Long todoId;
    private final String memo;

    public MemoResponse(Long todoId, String memo) {
        this.todoId = todoId;
        this.memo = memo;
    }

    public static MemoResponse from(Long todoId, String memo) {
        return new MemoResponse(todoId, memo);
    }
}
