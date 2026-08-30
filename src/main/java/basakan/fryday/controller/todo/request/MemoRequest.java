package basakan.fryday.controller.todo.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MemoRequest {

    /**
     * 설정할 메모 값 전체. null 또는 빈 문자열이면 메모를 비운다.
     * 반복 인스턴스에 적용하면 개별 override 로 저장되어 마스터 메모를 다시 상속하지 않는다.
     */
    @Size(max = 100, message = "메모는 최대 100자까지 가능합니다.")
    private String memo;

    public MemoRequest(String memo) {
        this.memo = memo;
    }
}
