package basakan.fryday.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class UserContext {

    private UserContext() {
    }

    public static Long getCurrentUserId() {
        return getCurrentPrincipal().userId();
    }

    public static String getCurrentDeviceId() {
        return getCurrentPrincipal().deviceId();
    }

    private static UserPrincipal getCurrentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("인증되지 않은 사용자입니다.");
        }
        return (UserPrincipal) authentication.getPrincipal();
    }
}
