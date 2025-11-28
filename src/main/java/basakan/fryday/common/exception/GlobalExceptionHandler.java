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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode.getMessage()));
    }

    // 2. @Valid 유효성 검사 실패 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.warn("Validaion Failed: {}", e.getMessage());
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

    // 3. 그 외 모든 예외 처리
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Internal Server Error", e);
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode.getMessage()));
    }
}
