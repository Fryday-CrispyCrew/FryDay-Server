package basakan.fryday.common.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class FieldErrorResponse {
    private final String field;   // 오류가 발생한 필드
    private final String message; // 오류 메시지
}
