package basakan.fryday.controller.todo;

import basakan.fryday.RestDocsSupport;
import basakan.fryday.common.config.SecurityConfig;
import basakan.fryday.common.security.JwtAuthenticationFilter;
import basakan.fryday.common.security.JwtTokenProvider;
import basakan.fryday.controller.todo.request.TodoAlarmRequest;
import basakan.fryday.service.todo.TodoAlarmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = TodoAlarmController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
        }
)
class TodoAlarmControllerTest extends RestDocsSupport {

    @MockitoBean
    private TodoAlarmService todoAlarmService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUpSecurityContext() {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(1L, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    @DisplayName("개별 투두 알림 설정 API")
    void setTodoAlarm() throws Exception {
        // given
        Long todoId = 1L;
        TodoAlarmRequest request = new TodoAlarmRequest(LocalDateTime.of(2025, 12, 26, 14, 30, 0));

        willDoNothing().given(todoAlarmService)
                .setTodoAlarm(anyLong(), anyLong(), any(LocalDateTime.class));

        // when & then
        mockMvc.perform(post("/api/todos/{todoId}/alarm", todoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("알림이 성공적으로 설정되었습니다."))
                .andDo(document("todo-alarm-set",
                        pathParameters(
                                parameterWithName("todoId").description("투두 ID")
                        ),
                        requestFields(
                                fieldWithPath("notifyAt").type(JsonFieldType.STRING).description("알림 시간 (ISO 8601 형식, 예: 2025-12-26T14:30:00)")
                        ),
                        responseFields(
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("개별 투두 알림 삭제 API")
    void deleteTodoAlarm() throws Exception {
        // given
        Long todoId = 1L;

        willDoNothing().given(todoAlarmService)
                .deleteTodoAlarm(anyLong(), anyLong());

        // when & then
        mockMvc.perform(delete("/api/todos/{todoId}/alarm", todoId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("알림이 성공적으로 삭제되었습니다."))
                .andDo(document("todo-alarm-delete",
                        pathParameters(
                                parameterWithName("todoId").description("투두 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지")
                        )
                ));
    }
}
