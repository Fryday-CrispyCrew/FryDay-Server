package basakan.fryday.common.exception;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("DataAccessResourceFailureException 발생 시 503 + SERVICE_UNAVAILABLE 에러 코드 반환")
    void handleDataAccessResourceFailureException() {
        DataAccessResourceFailureException exception = new DataAccessResourceFailureException("DB 연결 실패");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/todos");

        ResponseEntity<ApiResponse<Void>> response = handler.handleDatabaseConnectionException(exception, request);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE.getMessage());
        assertThat(response.getBody().getErrorCode()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    @DisplayName("CannotCreateTransactionException 발생 시 503 + SERVICE_UNAVAILABLE 에러 코드 반환")
    void handleCannotCreateTransactionException() {
        CannotCreateTransactionException exception = new CannotCreateTransactionException("트랜잭션 생성 실패");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/todos");

        ResponseEntity<ApiResponse<Void>> response = handler.handleDatabaseConnectionException(exception, request);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().getErrorCode()).isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    @DisplayName("성공 응답에는 errorCode가 null이다")
    void successResponseHasNoErrorCode() {
        ApiResponse<String> response = ApiResponse.success("data");

        assertThat(response.getErrorCode()).isNull();
        assertThat(response.isSuccess()).isTrue();
    }
}
