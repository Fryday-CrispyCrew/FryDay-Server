package basakan.fryday.controller.admin;

import basakan.fryday.RestDocsSupport;
import basakan.fryday.common.config.AdminWebConfig;
import basakan.fryday.common.config.SecurityConfig;
import basakan.fryday.common.security.JwtAuthenticationFilter;
import basakan.fryday.common.security.JwtTokenProvider;
import basakan.fryday.controller.admin.request.BroadcastPushRequest;
import basakan.fryday.service.admin.AdminPushService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AdminPushController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                        classes = {SecurityConfig.class, AdminWebConfig.class})
        }
)
class AdminPushControllerTest extends RestDocsSupport {

    @MockitoBean
    private AdminPushService adminPushService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("전체 푸시 발송 API - 성공")
    void broadcastPush() throws Exception {
        // given
        BroadcastPushRequest request = new BroadcastPushRequest("공지사항", "서버 점검 안내입니다");
        given(adminPushService.broadcastPush("공지사항", "서버 점검 안내입니다")).willReturn(15);

        // when & then
        mockMvc.perform(post("/api/admin/push")
                        .header("X-Admin-Key", "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sentCount").value(15))
                .andDo(document("admin-broadcast-push",
                        requestHeaders(
                                headerWithName("X-Admin-Key").description("Admin 인증 키")
                        ),
                        requestFields(
                                fieldWithPath("title").description("알림 제목 (최대 50자)"),
                                fieldWithPath("body").description("알림 내용 (최대 200자)")
                        ),
                        responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("data.sentCount").description("발송 성공 디바이스 수"),
                                fieldWithPath("timestamp").description("응답 시각")
                        )
                ));
    }

    @Test
    @DisplayName("전체 푸시 발송 API - 제목 누락 시 400")
    void broadcastPushWithoutTitle() throws Exception {
        // given
        BroadcastPushRequest request = new BroadcastPushRequest("", "내용");

        // when & then
        mockMvc.perform(post("/api/admin/push")
                        .header("X-Admin-Key", "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }
}
