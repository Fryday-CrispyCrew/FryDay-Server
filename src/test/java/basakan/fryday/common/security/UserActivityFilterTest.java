package basakan.fryday.common.security;

import basakan.fryday.service.user.UserActivityRecorder;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@DisplayName("일별 활동 기록 필터")
class UserActivityFilterTest {

    private final UserActivityRecorder recorder = mock(UserActivityRecorder.class);
    private final UserActivityFilter filter = new UserActivityFilter(recorder);
    private final FilterChain filterChain = mock(FilterChain.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/todos");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증된 요청은 유저의 활동을 기록하고 다음 필터로 넘긴다")
    void recordsAuthenticatedRequest() throws Exception {
        // given
        authenticate(1L);

        // when
        filter.doFilter(request, response, filterChain);

        // then
        then(recorder).should().record(1L);
        then(filterChain).should().doFilter(request, response);
    }

    @Test
    @DisplayName("인증되지 않은 요청은 기록하지 않는다")
    void skipsUnauthenticatedRequest() throws Exception {
        // when
        filter.doFilter(request, response, filterChain);

        // then
        then(recorder).should(never()).record(any());
        then(filterChain).should().doFilter(request, response);
    }

    @Test
    @DisplayName("기록 중 예외가 나도 요청은 계속 진행된다")
    void continuesWhenRecorderFails() throws Exception {
        // given
        authenticate(1L);
        willThrow(new RuntimeException("기록 실패")).given(recorder).record(1L);

        // when
        filter.doFilter(request, response, filterChain);

        // then
        then(filterChain).should().doFilter(request, response);
    }

    private void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }
}
