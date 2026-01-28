package basakan.fryday.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class UserContext {

    private UserContext() {
    }

    public static Long getCurrentUserId() {
        Authentication authentication = getAuthentication();
        return (Long) authentication.getPrincipal();
    }

    public static String getCurrentDeviceId() {
        Authentication authentication = getAuthentication();
        Object details = authentication.getDetails();
        if (details == null) {
            throw new IllegalStateException("디바이스 정보가 없습니다. 재로그인이 필요합니다.");
        }
        return (String) details;
    }

    private static Authentication getAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("인증되지 않은 사용자입니다.");
        }
        return authentication;
    }
}
