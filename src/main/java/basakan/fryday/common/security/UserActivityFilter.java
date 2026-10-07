package basakan.fryday.common.security;

import basakan.fryday.service.user.UserActivityRecorder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 인증이 끝난 요청의 유저를 그날 활동한 유저로 기록한다.
 * 빈으로 등록하면 @WebMvcTest 슬라이스가 이 필터를 끌어와 컨트롤러 테스트마다 기록기가 필요해지므로, SecurityConfig 에서 직접 생성한다.
 */
@Slf4j
@RequiredArgsConstructor
public class UserActivityFilter extends OncePerRequestFilter {

    private final UserActivityRecorder userActivityRecorder;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            try {
                userActivityRecorder.record(userId);
            } catch (RuntimeException e) {
                log.warn("일별 활동 기록 중 예외: userId={}", userId, e);
            }
        }

        filterChain.doFilter(request, response);
    }
}
