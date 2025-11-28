package basakan.fryday.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "잘못된 입력값입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    INVALID_TYPE_VALUE( HttpStatus.BAD_REQUEST, "잘못된 타입의 값입니다."),
    HANDLE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근이 거부되었습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),

    // Category
    CATEGORY_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "카테고리 개수는 최대 6개까지 생성할 수 있습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."),

    // Todo
    TODO_NOT_FOUND(HttpStatus.NOT_FOUND, "투두를 찾을 수 없습니다."),
    CANNOT_DELETE_FAILED_TODO(HttpStatus.BAD_REQUEST, "타버린(미완료) 투두는 삭제할 수 없습니다."),
    TODO_NOT_TODAY(HttpStatus.BAD_REQUEST, "오늘 날짜의 투두만 내일로 미룰 수 있습니다."),
    PAST_DATE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "과거 날짜로 변경할 수 없습니다."),

    // Auth
    PROVIDER_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "소셜 토큰이 유효하지 않거나 만료되었습니다."),
    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 Provider입니다."),
    USER_BLOCKED(HttpStatus.FORBIDDEN, "차단된 사용자입니다."),
    USER_WITHDRAWN(HttpStatus.FORBIDDEN, "탈퇴한 사용자입니다."),
    INTERNAL_AUTH_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "인증 처리 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}

