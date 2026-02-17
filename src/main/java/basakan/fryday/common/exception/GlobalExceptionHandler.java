package basakan.fryday.common.exception;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.auth.InvalidProviderTokenException;
import basakan.fryday.common.exception.auth.UnsupportedProviderException;
import basakan.fryday.common.exception.auth.UserBlockedException;
import basakan.fryday.common.exception.auth.UserWithdrawnException;
import basakan.fryday.common.response.ApiResponse;
import basakan.fryday.common.response.FieldErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. Auth 예외 처리 - InvalidProviderTokenException
    @ExceptionHandler(InvalidProviderTokenException.class)
    protected ResponseEntity<ApiResponse<Void>> handleInvalidProviderToken(InvalidProviderTokenException e) {
        log.warn("Invalid Provider Token: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail("소셜 로그인 토큰이 유효하지 않습니다."));
    }

    // 2. Auth 예외 처리 - UnsupportedProviderException
    @ExceptionHandler(UnsupportedProviderException.class)
    protected ResponseEntity<ApiResponse<Void>> handleUnsupportedProvider(UnsupportedProviderException e) {
        log.warn("Unsupported Provider: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("지원하지 않는 소셜 로그인 Provider입니다. (KAKAO, NAVER, APPLE만 지원)"));
    }

    // 3. Auth 예외 처리 - UserBlockedException
    @ExceptionHandler(UserBlockedException.class)
    protected ResponseEntity<ApiResponse<Void>> handleUserBlocked(UserBlockedException e) {
        log.warn("User Blocked: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail("차단된 사용자입니다. 관리자에게 문의하세요."));
    }

    // 4. Auth 예외 처리 - UserWithdrawnException
    @ExceptionHandler(UserWithdrawnException.class)
    protected ResponseEntity<ApiResponse<Void>> handleUserWithdrawn(UserWithdrawnException e) {
        log.warn("User Withdrawn: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail("탈퇴한 사용자입니다."));
    }

    // 5. 비즈니스 로직 예외 처리
    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("BusinessException: {}", e.getMessage());
        ErrorCode errorCode = e.getErrorCode();

        // 커스텀 메시지가 있으면 사용, 없으면 ErrorCode의 기본 메시지 사용
        String message = e.getMessage() != null && !e.getMessage().equals(errorCode.getMessage())
                ? e.getMessage()
                : errorCode.getMessage();

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.fail(message));
    }

    // 6. @Valid 유효성 검사 실패 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.warn("Validation Failed: {}", e.getMessage());
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;

        List<FieldErrorResponse> fieldErrors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> new FieldErrorResponse(
                        fieldError.getField(),
                        fieldError.getDefaultMessage()
                ))
                .collect(Collectors.toList());

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode.getMessage(), fieldErrors));
    }

    // 7. 잘못된 경로 (404 Not Found)
    @ExceptionHandler(NoHandlerFoundException.class)
    protected ResponseEntity<ApiResponse<Void>> handleNoHandlerFoundException(NoHandlerFoundException e) {
        log.warn("No Handler Found: {} {}", e.getHttpMethod(), e.getRequestURL());
        ErrorCode errorCode = ErrorCode.NOT_FOUND;

        String message = String.format("요청한 경로를 찾을 수 없습니다. (경로: %s %s)", 
                e.getHttpMethod(), e.getRequestURL());

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.fail(message));
    }

    // 8. 잘못된 HTTP 메서드 (405 Method Not Allowed)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    protected ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e) {
        log.warn("Method Not Allowed: {} (Supported: {})", 
                e.getMethod(), e.getSupportedHttpMethods());

        String supportedMethods = e.getSupportedHttpMethods() != null
                ? e.getSupportedHttpMethods().toString()
                : "없음";

        String message = String.format("지원하지 않는 HTTP 메서드입니다. 요청 메서드: %s, 지원 메서드: %s",
                e.getMethod(), supportedMethods);

        return ResponseEntity
                .status(ErrorCode.METHOD_NOT_ALLOWED.getStatus())
                .body(ApiResponse.fail(message));
    }

    // 9. 지원하지 않는 미디어 타입 (415 Unsupported Media Type)
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    protected ResponseEntity<ApiResponse<Void>> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException e) {
        log.warn("Unsupported Media Type: {} (Supported: {})", 
                e.getContentType(), e.getSupportedMediaTypes());

        String supportedTypes = e.getSupportedMediaTypes().toString();
        String message = String.format("지원하지 않는 Content-Type입니다. 요청 타입: %s, 지원 타입: %s",
                e.getContentType(), supportedTypes);

        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.fail(message));
    }

    // 10. 필수 요청 파라미터 누락 (400 Bad Request)
    @ExceptionHandler(MissingServletRequestParameterException.class)
    protected ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e) {
        log.warn("Missing Request Parameter: {} (Type: {})", 
                e.getParameterName(), e.getParameterType());

        String message = String.format("필수 파라미터가 누락되었습니다. 파라미터명: %s (타입: %s)",
                e.getParameterName(), e.getParameterType());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(message));
    }

    // 11-1. 요청 파라미터 타입 불일치 (400 Bad Request)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e) {
        log.warn("Type Mismatch: {} (Required: {}, Provided: {})", 
                e.getName(), e.getRequiredType(), e.getValue());

        String parameterName = e.getName();
        String requiredType = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "알 수 없음";
        String providedValue = e.getValue() != null ? e.getValue().toString() : "null";

        String message = String.format("파라미터 타입이 올바르지 않습니다. 파라미터명: %s, 요구 타입: %s, 제공된 값: %s",
                parameterName, requiredType, providedValue);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(message));
    }

    // 11-2. 요청 본문 파싱 실패 (400 Bad Request)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    protected ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e) {
        log.warn("HTTP Message Not Readable: {}", e.getMessage());

        String message = extractReadableMessage(e);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(message));
    }

    /**
     * Enum 값 오류, JSON 형식 오류 등을 구분하여 명확한 메시지 제공
     */
    private String extractReadableMessage(HttpMessageNotReadableException e) {
        String errorMessage = e.getMessage();
        Throwable cause = e.getCause();

        // IllegalArgumentException이 원인인 경우
        if (cause instanceof IllegalArgumentException) {
            String causeMessage = cause.getMessage();
            if (causeMessage != null) {
                // Enum 관련 에러인지 확인
                if (causeMessage.contains("존재하지 않는") || 
                    causeMessage.contains("테마 색상") ||
                    causeMessage.contains("CategoryColor") ||
                    causeMessage.contains("RecurrenceType") ||
                    causeMessage.contains("Status") ||
                    causeMessage.contains("enum")) {
                    return "지원하지 않는 Enum 값입니다. 요청 값을 확인해주세요.";
                }
                return causeMessage;
            }
        }

        // JSON 파싱 오류인 경우
        if (errorMessage != null) {
            if (errorMessage.contains("JSON parse error") || errorMessage.contains("Cannot deserialize")) {
                // Enum 관련 에러인지 확인
                if (errorMessage.contains("enum") || 
                    errorMessage.contains("CategoryColor") || 
                    errorMessage.contains("RecurrenceType") || 
                    errorMessage.contains("Status") ||
                    errorMessage.contains("Role") ||
                    errorMessage.contains("AccountStatus") ||
                    errorMessage.contains("AlarmStatus") ||
                    errorMessage.contains("ExceptionType") ||
                    errorMessage.contains("CharacterStatus")) {
                    return "지원하지 않는 Enum 값입니다. 요청 값을 확인해주세요.";
                }
                return "요청 본문의 JSON 형식이 올바르지 않습니다. 필드 타입과 값을 확인해주세요.";
            }
            if (errorMessage.contains("Required request body is missing")) {
                return "요청 본문이 필요합니다.";
            }
        }

        // 기본 메시지
        return "요청 본문을 읽을 수 없습니다. JSON 형식이 올바른지 확인해주세요.";
    }

    // 12. 그 외 모든 예외 처리
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ApiResponse<Void>> handleException(Exception e, HttpServletRequest request) {
        log.error("ERROR [{} {}] 예외타입: {} 메시지: {}", request.getMethod(), request.getRequestURI(),
                e.getClass().getSimpleName(), e.getMessage(), e);
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode.getMessage()));
    }
}
