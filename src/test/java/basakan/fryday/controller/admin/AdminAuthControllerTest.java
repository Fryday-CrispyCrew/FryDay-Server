package basakan.fryday.controller.admin;

import basakan.fryday.RestDocsSupport;
import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.config.SecurityConfig;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.common.security.JwtAuthenticationFilter;
import basakan.fryday.common.security.JwtTokenProvider;
import basakan.fryday.controller.admin.request.AdminLoginRequest;
import basakan.fryday.controller.admin.response.AdminLoginResponse;
import basakan.fryday.service.admin.AdminAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AdminAuthController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
        }
)
class AdminAuthControllerTest extends RestDocsSupport {

    @MockitoBean
    private AdminAuthService adminAuthService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("어드민 로그인 API - 성공")
    void login() throws Exception {
        // given
        AdminLoginRequest request = new AdminLoginRequest("admin", "password");
        given(adminAuthService.login("admin", "password"))
                .willReturn(new AdminLoginResponse("admin-access-token", 43200, "운영자"));

        // when & then
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("admin-access-token"))
                .andExpect(jsonPath("$.data.expiresIn").value(43200))
                .andDo(document("admin-login",
                        requestFields(
                                fieldWithPath("loginId").description("어드민 아이디"),
                                fieldWithPath("password").description("비밀번호")
                        ),
                        responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("data.accessToken").description("어드민 API 호출용 토큰. Authorization: Bearer 로 보낸다"),
                                fieldWithPath("data.expiresIn").description("토큰 만료까지 남은 시간 (초). 만료되면 다시 로그인한다"),
                                fieldWithPath("data.name").description("어드민 이름"),
                                fieldWithPath("timestamp").description("응답 시각")
                        )
                ));
    }

    @Test
    @DisplayName("어드민 로그인 API - 아이디나 비밀번호가 틀리면 401")
    void loginFailed() throws Exception {
        // given
        AdminLoginRequest request = new AdminLoginRequest("admin", "wrong");
        given(adminAuthService.login("admin", "wrong"))
                .willThrow(new BusinessException(ErrorCode.ADMIN_LOGIN_FAILED));

        // when & then
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(ErrorCode.ADMIN_LOGIN_FAILED.getMessage()));
    }

    @Test
    @DisplayName("어드민 로그인 API - 비밀번호 누락 시 400")
    void loginWithoutPassword() throws Exception {
        // given
        AdminLoginRequest request = new AdminLoginRequest("admin", "");

        // when & then
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
