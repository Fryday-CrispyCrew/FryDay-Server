package basakan.fryday.common.security;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.config.SecurityConfig;
import basakan.fryday.controller.admin.AdminPushController;
import basakan.fryday.controller.admin.request.BroadcastPushRequest;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.admin.AdminAccountRepository;
import basakan.fryday.service.admin.AdminPushService;
import basakan.fryday.service.user.UserActivityRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 실제 SecurityConfig 체인을 태워서 어드민 경로의 인증 방식별 결과를 확인한다
@WebMvcTest(controllers = AdminPushController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, AdminJwtTokenProvider.class})
@DisplayName("어드민 API 인증")
class AdminSecurityTest {

    private static final Long ADMIN_ID = 1L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AdminJwtTokenProvider adminJwtTokenProvider;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    @MockitoBean private AdminPushService adminPushService;
    @MockitoBean private AdminAccountRepository adminAccountRepository;
    @MockitoBean private UserActivityRecorder userActivityRecorder;

    @Test
    @DisplayName("어드민 토큰이면 통과하고, 일별 활동으로 기록하지 않는다")
    void adminToken() throws Exception {
        given(adminAccountRepository.existsById(ADMIN_ID)).willReturn(true);

        push("Authorization", "Bearer " + adminJwtTokenProvider.generateAccessToken(ADMIN_ID))
                .andExpect(status().isOk());

        verify(userActivityRecorder, never()).record(anyLong());
    }

    @Test
    @DisplayName("인증 정보가 없으면 401 과 공통 응답 형식으로 내려준다")
    void noCredentials() throws Exception {
        push(null, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.ADMIN_UNAUTHORIZED.getMessage()));
    }

    @Test
    @DisplayName("앱 유저 토큰으로는 어드민 API 를 호출할 수 없다")
    void appUserToken() throws Exception {
        String userToken = jwtTokenProvider.generateAccessToken(
                ADMIN_ID, User.Role.ADMIN, User.AccountStatus.ACTIVE, "device-1");

        push("Authorization", "Bearer " + userToken)
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("계정이 삭제되면 남아 있는 토큰도 막힌다")
    void deletedAdmin() throws Exception {
        given(adminAccountRepository.existsById(ADMIN_ID)).willReturn(false);

        push("Authorization", "Bearer " + adminJwtTokenProvider.generateAccessToken(ADMIN_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("X-Admin-Key 가 맞으면 통과하고, 틀리면 401")
    void adminApiKey() throws Exception {
        push("X-Admin-Key", "test-admin-key").andExpect(status().isOk());
        push("X-Admin-Key", "wrong-key").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("어드민 토큰으로는 앱 API 를 호출할 수 없다")
    void adminTokenOnAppApi() throws Exception {
        mockMvc.perform(get("/api/todos")
                        .header("Authorization", "Bearer " + adminJwtTokenProvider.generateAccessToken(ADMIN_ID)))
                .andExpect(status().isForbidden());
    }

    private ResultActions push(String headerName, String headerValue) throws Exception {
        var request = post("/api/admin/push")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BroadcastPushRequest("공지", "내용")));
        if (headerName != null) {
            request.header(headerName, headerValue);
        }
        return mockMvc.perform(request);
    }
}
