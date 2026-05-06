package basakan.fryday.common.security;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminKeyInterceptorTest {

    @Test
    @DisplayName("유효한 Admin Key면 통과한다")
    void validAdminKey() {
        AdminKeyInterceptor interceptor = new AdminKeyInterceptor("my-secret-key");
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        when(request.getHeader("X-Admin-Key")).thenReturn("my-secret-key");

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Admin Key가 없으면 BusinessException 발생")
    void missingAdminKey() {
        AdminKeyInterceptor interceptor = new AdminKeyInterceptor("my-secret-key");
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        when(request.getHeader("X-Admin-Key")).thenReturn(null);

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ADMIN_KEY_INVALID);
    }

    @Test
    @DisplayName("Admin Key가 틀리면 BusinessException 발생")
    void invalidAdminKey() {
        AdminKeyInterceptor interceptor = new AdminKeyInterceptor("my-secret-key");
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        when(request.getHeader("X-Admin-Key")).thenReturn("wrong-key");

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ADMIN_KEY_INVALID);
    }

    @Test
    @DisplayName("Admin Key가 설정되지 않으면 모든 요청 거부")
    void adminKeyNotConfigured() {
        AdminKeyInterceptor interceptor = new AdminKeyInterceptor(null);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        when(request.getHeader("X-Admin-Key")).thenReturn("any-key");

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ADMIN_KEY_INVALID);
    }
}
