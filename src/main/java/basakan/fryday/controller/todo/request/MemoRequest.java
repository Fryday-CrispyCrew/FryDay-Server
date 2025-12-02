package basakan.fryday.controller.todo.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MemoRequest {

    @Size(max = 300, message = "메모는 최대 300자까지 가능합니다.")
    private String memo;

    public MemoRequest(String memo) {
        this.memo = memo;
    }
}
