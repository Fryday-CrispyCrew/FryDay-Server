package basakan.fryday.common.security;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

public class AdminKeyInterceptor implements HandlerInterceptor {

    private static final String ADMIN_KEY_HEADER = "X-Admin-Key";

    private final String adminApiKey;

    public AdminKeyInterceptor(String adminApiKey) {
        this.adminApiKey = adminApiKey;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (adminApiKey == null || adminApiKey.isBlank()) {
            throw new BusinessException(ErrorCode.ADMIN_KEY_INVALID);
        }

        String requestKey = request.getHeader(ADMIN_KEY_HEADER);
        if (!adminApiKey.equals(requestKey)) {
            throw new BusinessException(ErrorCode.ADMIN_KEY_INVALID);
        }

        return true;
    }
}
