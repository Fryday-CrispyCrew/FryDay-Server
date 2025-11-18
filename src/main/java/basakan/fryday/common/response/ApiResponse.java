package basakan.fryday.common.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class ApiResponse<T> {

    private final boolean success;
    private final String message;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final T data;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final List<FieldErrorResponse> errors;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime timestamp;

    private ApiResponse(boolean success, String message, T data, List<FieldErrorResponse> errors) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.errors = errors;
        this.timestamp = LocalDateTime.now();
    }

    // 성공 응답 (데이터)
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "정상 처리되었습니다.", data, null);
    }

    // 성공 응답 (커스텀 메시지 + 데이터)
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data, null);
    }

    // 실패 응답 (간단한 메시지)
    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, message, null, null);
    }

    public static <T> ApiResponse<T> fail(String message, List<FieldErrorResponse> errors) {
        return new ApiResponse<>(false, message, null, errors);
    }
}