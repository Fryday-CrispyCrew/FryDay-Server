package basakan.fryday.controller;

import basakan.fryday.RestDocsSupport;
import basakan.fryday.controller.dto.TodoResponse;
import basakan.fryday.controller.dto.TodoSaveRequest;
import basakan.fryday.domain.Category;
import basakan.fryday.domain.CategoryColor;
import basakan.fryday.domain.Todo;
import basakan.fryday.service.TodoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TodoController.class)
class TodoControllerTest extends RestDocsSupport {

    @MockitoBean
    private TodoService todoService;


    @Test
    @DisplayName("투두 생성 API")
    void createTodo() throws Exception {
        // given
        Long categoryId = 1L;
        TodoSaveRequest request = new TodoSaveRequest("양파 썰기", categoryId);

        // Mocking을 위한 가짜 데이터 생성
        Category mockCategory = Category.builder()
                .name("요리")
                .color(CategoryColor.BR)
                .userId(1L)
                .build();
        ReflectionTestUtils.setField(mockCategory, "id", categoryId);

        Todo mockTodo = Todo.builder()
                .description("양파 썰기")
                .category(mockCategory)
                .build();
        ReflectionTestUtils.setField(mockTodo, "id", 1L);

        // Service가 호출되면 위에서 만든 가짜 TodoResponse를 반환하도록 설정
        given(todoService.saveTodo(any(TodoSaveRequest.class)))
                .willReturn(TodoResponse.from(mockTodo));

        // when & then
        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("todo-create",
                        requestFields(
                                fieldWithPath("description").type(JsonFieldType.STRING).description("할 일 내용"),
                                fieldWithPath("categoryId").type(JsonFieldType.NUMBER).description("카테고리 ID")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),

                                fieldWithPath("data.id").type(JsonFieldType.NUMBER).description("생성된 투두 ID"),
                                fieldWithPath("data.description").type(JsonFieldType.STRING).description("할 일 내용"),
                                fieldWithPath("data.status").type(JsonFieldType.STRING).description("상태 (IN_PROGRESS, COMPLETED, FAILED)"),
                                fieldWithPath("data.categoryId").type(JsonFieldType.NUMBER).description("카테고리 ID"),

                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("투두 완료 토글 API")
    void toggleTodoCompletion() throws Exception {
        // given
        Long todoId = 1L;

        Category mockCategory = Category.builder().name("요리").color(CategoryColor.BR).userId(1L).build();
        ReflectionTestUtils.setField(mockCategory, "id", 1L);

        Todo mockTodo = Todo.builder().description("양파 썰기").category(mockCategory).build();
        ReflectionTestUtils.setField(mockTodo, "id", todoId);

        // 강제로 완료 상태로 변경 (테스트용)
        mockTodo.toggleCompletion();

        given(todoService.toggleTodoCompletion(anyLong(), anyLong()))
                .willReturn(TodoResponse.from(mockTodo));

        // when & then
        mockMvc.perform(post("/api/todos/{todoId}/completion", todoId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("todo-toggle",
                        pathParameters(
                                parameterWithName("todoId").description("상태를 변경할 투두 ID")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),

                                fieldWithPath("data.id").type(JsonFieldType.NUMBER).description("투두 ID"),
                                fieldWithPath("data.description").type(JsonFieldType.STRING).description("할 일 내용"),
                                fieldWithPath("data.status").type(JsonFieldType.STRING).description("변경 후 상태 (IN_PROGRESS <-> COMPLETED)"),
                                fieldWithPath("data.categoryId").type(JsonFieldType.NUMBER).description("카테고리 ID"),

                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }
}