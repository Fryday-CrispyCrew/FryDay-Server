package basakan.fryday.controller.notification;

import basakan.fryday.RestDocsSupport;
import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.config.SecurityConfig;
import basakan.fryday.common.security.JwtAuthenticationFilter;
import basakan.fryday.common.security.JwtTokenProvider;
import basakan.fryday.controller.notification.request.NotificationOpenRequest;
import basakan.fryday.service.notification.NotificationOpenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = NotificationController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
        }
)
@DisplayName("알림 API")
class NotificationControllerTest extends RestDocsSupport {

    private static final Long USER_ID = 1L;

    @MockitoBean private NotificationOpenService notificationOpenService;
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUpSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID, null, Collections.emptyList()));
    }

    @Test
    @DisplayName("알림 오픈 기록 API")
    void recordOpen() throws Exception {
        // when & then
        mockMvc.perform(post("/api/notifications/opens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new NotificationOpenRequest("background", "GROUP_JOINED", 12L))))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("notification-open",
                        requestFields(
                                fieldWithPath("trigger").type(JsonFieldType.STRING)
                                        .description("알림을 연 상황. `foreground`(앱 사용 중), `background`(백그라운드), "
                                                + "`cold_start`(앱이 꺼진 상태에서 실행)"),
                                fieldWithPath("type").type(JsonFieldType.STRING).optional()
                                        .description("푸시 `data` 의 `type` 값. 없으면 보내지 않는다. "
                                                + "영문, 숫자, `-_.~%` 만 쓸 수 있고 50자 이하"),
                                fieldWithPath("groupId").type(JsonFieldType.NUMBER).optional()
                                        .description("푸시 `data` 의 `groupId` 값. 없으면 보내지 않는다. 문자열(\"12\")로 보내도 된다")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));

        then(notificationOpenService).should().recordOpen(any(NotificationOpenRequest.class), eq(USER_ID));
    }

    @Test
    @DisplayName("알림 종류와 그룹 ID 없이 열린 상황만으로 기록할 수 있다")
    void recordOpenWithTriggerOnly() throws Exception {
        mockMvc.perform(post("/api/notifications/opens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trigger\":\"cold_start\"}"))
                .andDo(print())
                .andExpect(status().isOk());

        NotificationOpenRequest request = captureRequest();
        assertThat(request.getTrigger()).isEqualTo("cold_start");
        assertThat(request.getType()).isNull();
        assertThat(request.getGroupId()).isNull();
    }

    @Test
    @DisplayName("푸시 data 처럼 문자열로 온 그룹 ID 도 숫자로 받는다")
    void recordOpenWithStringGroupId() throws Exception {
        mockMvc.perform(post("/api/notifications/opens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trigger\":\"foreground\",\"type\":\"GROUP_INTERACTION\",\"groupId\":\"12\"}"))
                .andDo(print())
                .andExpect(status().isOk());

        assertThat(captureRequest().getGroupId()).isEqualTo(12L);
    }

    @Test
    @DisplayName("숫자가 아닌 그룹 ID 는 400 으로 응답한다")
    void recordOpenWithNonNumericGroupIdFails() throws Exception {
        mockMvc.perform(post("/api/notifications/opens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trigger\":\"foreground\",\"groupId\":\"abc\"}"))
                .andDo(print())
                .andExpect(status().isBadRequest());

        then(notificationOpenService).should(never()).recordOpen(any(), anyLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"trigger\":\"BACKGROUND\"}",
            "{\"trigger\":\"opened\"}"
    })
    @DisplayName("열린 상황이 없거나 허용하지 않는 값이면 400 으로 응답한다")
    void recordOpenWithInvalidTriggerFails(String body) throws Exception {
        mockMvc.perform(post("/api/notifications/opens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_INPUT_VALUE.getMessage()))
                .andExpect(jsonPath("$.errors[0].field").value("trigger"));

        then(notificationOpenService).should(never()).recordOpen(any(), anyLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"trigger\":\"background\",\"type\":\"\"}",
            "{\"trigger\":\"background\",\"type\":\"GROUP JOINED\"}",
            "{\"trigger\":\"background\",\"type\":\"그룹참여\"}",
            "{\"trigger\":\"background\",\"type\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"}"
    })
    @DisplayName("알림 종류가 analytics label 형식이 아니면 400 으로 응답한다")
    void recordOpenWithInvalidTypeFails(String body) throws Exception {
        mockMvc.perform(post("/api/notifications/opens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("type"));
    }

    @Test
    @DisplayName("그룹 ID 가 양수가 아니면 400 으로 응답한다")
    void recordOpenWithNonPositiveGroupIdFails() throws Exception {
        mockMvc.perform(post("/api/notifications/opens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trigger\":\"background\",\"groupId\":0}"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("groupId"));
    }

    private NotificationOpenRequest captureRequest() {
        ArgumentCaptor<NotificationOpenRequest> captor = ArgumentCaptor.forClass(NotificationOpenRequest.class);
        then(notificationOpenService).should().recordOpen(captor.capture(), eq(USER_ID));
        return captor.getValue();
    }
}
