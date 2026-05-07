package basakan.fryday.controller.todo;

import basakan.fryday.RestDocsSupport;
import basakan.fryday.common.config.SecurityConfig;
import basakan.fryday.common.security.JwtAuthenticationFilter;
import basakan.fryday.common.security.JwtTokenProvider;
import basakan.fryday.controller.todo.request.InstanceDeleteRequest;
import basakan.fryday.controller.todo.request.InstanceEditRequest;
import basakan.fryday.service.todo.RecurrenceInstanceService;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = RecurrenceInstanceController.class,
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
        }
)
class RecurrenceInstanceControllerTest extends RestDocsSupport {

    @MockitoBean
    private RecurrenceInstanceService recurrenceInstanceService;

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
    @DisplayName("반복 인스턴스 수정 - 이번만 (THIS)")
    void editInstance_this() throws Exception {
        long instanceId = 1L;
        willDoNothing().given(recurrenceInstanceService)
                .edit(anyLong(), any(), any(), anyLong());

        Map<String, Object> payload = Map.of(
                "title", "수정된 제목",
                "memo", "수정된 메모",
                "isAlarmEnabled", true,
                "alarmTime", "09:00:00"
        );
        Map<String, Object> request = Map.of(
                "scope", "THIS",
                "payload", payload
        );

        mockMvc.perform(put("/api/todos/instances/{instanceId}/edit", instanceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("instance-edit-this",
                        pathParameters(
                                parameterWithName("instanceId").description("수정할 반복 인스턴스(투두) ID")
                        ),
                        requestFields(
                                fieldWithPath("scope").type(JsonFieldType.STRING)
                                        .description("수정 범위: THIS(이번만) / THIS_AND_FUTURE(이번 이후 전체) / ALL(전체)"),
                                fieldWithPath("payload").type(JsonFieldType.OBJECT)
                                        .description("수정할 내용. content 필드와 rule 필드를 함께 사용할 수 있음"),
                                fieldWithPath("payload.title").type(JsonFieldType.STRING)
                                        .description("[content] 변경할 제목").optional(),
                                fieldWithPath("payload.memo").type(JsonFieldType.STRING)
                                        .description("[content] 변경할 메모").optional(),
                                fieldWithPath("payload.isAlarmEnabled").type(JsonFieldType.BOOLEAN)
                                        .description("[content] 알람 활성화 여부").optional(),
                                fieldWithPath("payload.alarmTime").type(JsonFieldType.STRING)
                                        .description("[content] 알람 시간 HH:mm:ss").optional(),
                                fieldWithPath("payload.type").type(JsonFieldType.STRING)
                                        .description("[rule] 반복 주기 (DAILY/WEEKLY/MONTHLY/YEARLY). scope=THIS 에서는 무시됨").optional(),
                                fieldWithPath("payload.frequencyValues").type(JsonFieldType.ARRAY)
                                        .description("[rule] 반복 상세 값 (요일, 날짜 등). scope=THIS 에서는 무시됨").optional(),
                                fieldWithPath("payload.startDate").type(JsonFieldType.STRING)
                                        .description("[rule] 반복 시작일 (YYYY-MM-DD). scope=ALL 에서만 사용됨").optional(),
                                fieldWithPath("payload.endDate").type(JsonFieldType.STRING)
                                        .description("[rule] 반복 종료일 (YYYY-MM-DD). null이면 현재 종료 조건 유지. scope=THIS 에서는 무시됨").optional()
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("반복 인스턴스 수정 - 이번 이후 전체 (THIS_AND_FUTURE)")
    void editInstance_thisAndFuture() throws Exception {
        long instanceId = 1L;
        willDoNothing().given(recurrenceInstanceService)
                .edit(anyLong(), any(), any(), anyLong());

        Map<String, Object> payload = Map.of(
                "title", "이후 수정 제목",
                "type", "WEEKLY",
                "frequencyValues", List.of("MONDAY", "WEDNESDAY")
        );
        Map<String, Object> request = Map.of(
                "scope", "THIS_AND_FUTURE",
                "payload", payload
        );

        mockMvc.perform(put("/api/todos/instances/{instanceId}/edit", instanceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("instance-edit-this-and-future",
                        pathParameters(
                                parameterWithName("instanceId").description("수정할 반복 인스턴스(투두) ID")
                        ),
                        requestFields(
                                fieldWithPath("scope").type(JsonFieldType.STRING)
                                        .description("수정 범위: THIS_AND_FUTURE — 이 인스턴스 날짜부터 이후에 적용. 기존 Master 종료 후 새 Master 생성"),
                                fieldWithPath("payload").type(JsonFieldType.OBJECT)
                                        .description("수정할 내용"),
                                fieldWithPath("payload.title").type(JsonFieldType.STRING)
                                        .description("[content] 변경할 제목").optional(),
                                fieldWithPath("payload.memo").type(JsonFieldType.STRING)
                                        .description("[content] 변경할 메모").optional(),
                                fieldWithPath("payload.isAlarmEnabled").type(JsonFieldType.BOOLEAN)
                                        .description("[content] 알람 활성화 여부").optional(),
                                fieldWithPath("payload.alarmTime").type(JsonFieldType.STRING)
                                        .description("[content] 알람 시간 HH:mm:ss").optional(),
                                fieldWithPath("payload.type").type(JsonFieldType.STRING)
                                        .description("[rule] 반복 주기 (DAILY/WEEKLY/MONTHLY/YEARLY)").optional(),
                                fieldWithPath("payload.frequencyValues").type(JsonFieldType.ARRAY)
                                        .description("[rule] 반복 상세 값 (요일, 날짜 등)").optional(),
                                fieldWithPath("payload.endDate").type(JsonFieldType.STRING)
                                        .description("[rule] 반복 종료일 (YYYY-MM-DD). null이면 기존 종료 조건 인계").optional()
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("반복 인스턴스 수정 - 전체 (ALL)")
    void editInstance_all() throws Exception {
        long instanceId = 1L;
        willDoNothing().given(recurrenceInstanceService)
                .edit(anyLong(), any(), any(), anyLong());

        Map<String, Object> payload = Map.of(
                "title", "전체 수정 제목",
                "isAlarmEnabled", false
        );
        Map<String, Object> request = Map.of(
                "scope", "ALL",
                "payload", payload
        );

        mockMvc.perform(put("/api/todos/instances/{instanceId}/edit", instanceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("instance-edit-all",
                        pathParameters(
                                parameterWithName("instanceId").description("수정할 반복 인스턴스(투두) ID")
                        ),
                        requestFields(
                                fieldWithPath("scope").type(JsonFieldType.STRING)
                                        .description("수정 범위: ALL — 반복 규칙 전체에 적용 (개별 override된 인스턴스는 유지)"),
                                fieldWithPath("payload").type(JsonFieldType.OBJECT)
                                        .description("수정할 내용. content 필드와 rule 필드를 함께 사용할 수 있음"),
                                fieldWithPath("payload.title").type(JsonFieldType.STRING)
                                        .description("[content] 변경할 제목").optional(),
                                fieldWithPath("payload.memo").type(JsonFieldType.STRING)
                                        .description("[content] 변경할 메모").optional(),
                                fieldWithPath("payload.isAlarmEnabled").type(JsonFieldType.BOOLEAN)
                                        .description("[content] 알람 활성화 여부").optional(),
                                fieldWithPath("payload.alarmTime").type(JsonFieldType.STRING)
                                        .description("[content] 알람 시간 HH:mm:ss").optional(),
                                fieldWithPath("payload.type").type(JsonFieldType.STRING)
                                        .description("[rule] 반복 주기 (DAILY/WEEKLY/MONTHLY/YEARLY)").optional(),
                                fieldWithPath("payload.frequencyValues").type(JsonFieldType.ARRAY)
                                        .description("[rule] 반복 상세 값 (요일, 날짜 등)").optional(),
                                fieldWithPath("payload.startDate").type(JsonFieldType.STRING)
                                        .description("[rule] 반복 시작일 (YYYY-MM-DD). scope=ALL 에서만 사용됨").optional(),
                                fieldWithPath("payload.endDate").type(JsonFieldType.STRING)
                                        .description("[rule] 반복 종료일 (YYYY-MM-DD). null이면 현재 종료 조건 유지").optional()
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("반복 인스턴스 삭제 - 이번만 (THIS)")
    void deleteInstance_this() throws Exception {
        long instanceId = 1L;
        willDoNothing().given(recurrenceInstanceService)
                .delete(anyLong(), any(), anyLong());

        Map<String, Object> request = Map.of("scope", "THIS");

        mockMvc.perform(delete("/api/todos/instances/{instanceId}", instanceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("instance-delete-this",
                        pathParameters(
                                parameterWithName("instanceId").description("삭제할 반복 인스턴스(투두) ID")
                        ),
                        requestFields(
                                fieldWithPath("scope").type(JsonFieldType.STRING)
                                        .description("삭제 범위: THIS(이번만) / THIS_AND_FUTURE(이번 이후 전체) / ALL(전체)")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("반복 인스턴스 삭제 - 이번 이후 전체 (THIS_AND_FUTURE)")
    void deleteInstance_thisAndFuture() throws Exception {
        long instanceId = 1L;
        willDoNothing().given(recurrenceInstanceService)
                .delete(anyLong(), any(), anyLong());

        Map<String, Object> request = Map.of("scope", "THIS_AND_FUTURE");

        mockMvc.perform(delete("/api/todos/instances/{instanceId}", instanceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("instance-delete-this-and-future",
                        pathParameters(
                                parameterWithName("instanceId").description("삭제할 반복 인스턴스(투두) ID")
                        ),
                        requestFields(
                                fieldWithPath("scope").type(JsonFieldType.STRING)
                                        .description("삭제 범위: THIS_AND_FUTURE — 이 날짜부터 이후 모든 인스턴스 삭제 및 반복 규칙 종료")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("반복 인스턴스 삭제 - 전체 (ALL)")
    void deleteInstance_all() throws Exception {
        long instanceId = 1L;
        willDoNothing().given(recurrenceInstanceService)
                .delete(anyLong(), any(), anyLong());

        Map<String, Object> request = Map.of("scope", "ALL");

        mockMvc.perform(delete("/api/todos/instances/{instanceId}", instanceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("instance-delete-all",
                        pathParameters(
                                parameterWithName("instanceId").description("삭제할 반복 인스턴스(투두) ID")
                        ),
                        requestFields(
                                fieldWithPath("scope").type(JsonFieldType.STRING)
                                        .description("삭제 범위: ALL — 반복 규칙 전체 및 모든 인스턴스 삭제")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }
}
