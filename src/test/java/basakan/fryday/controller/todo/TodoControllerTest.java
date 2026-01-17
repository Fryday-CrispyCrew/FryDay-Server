package basakan.fryday.controller.todo;

import basakan.fryday.RestDocsSupport;
import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.config.SecurityConfig;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.common.security.JwtAuthenticationFilter;
import basakan.fryday.common.security.JwtTokenProvider;
import basakan.fryday.controller.todo.request.*;
import basakan.fryday.controller.dto.OrderUpdateRequest;
import basakan.fryday.controller.todo.response.*;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.todo.CharacterStatus;
import basakan.fryday.domain.todo.Recurrence;
import basakan.fryday.domain.todo.RecurrenceType;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.service.todo.RecurrenceService;
import basakan.fryday.service.todo.TodoService;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = TodoController.class,
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
        }
)
class TodoControllerTest extends RestDocsSupport {

    @MockitoBean
    private TodoService todoService;

    @MockitoBean
    private RecurrenceService recurrenceService;

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
    @DisplayName("투두 생성 API")
    void createTodo() throws Exception {
        // given
        Long categoryId = 1L;
        LocalDate todoDate = LocalDate.now();
        TodoSaveRequest request = new TodoSaveRequest("양파 썰기", categoryId, todoDate);

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
                .date(todoDate)
                .build();

        ReflectionTestUtils.setField(mockTodo, "id", 1L);
//        ReflectionTestUtils.setField(mockTodo, "status", Todo.Status.IN_PROGRESS);
//        ReflectionTestUtils.setField(mockTodo, "date", LocalDate.now());
//        ReflectionTestUtils.setField(mockTodo, "isBurnt", false);
//        ReflectionTestUtils.setField(mockTodo, "displayOrder", 1L);
//        ReflectionTestUtils.setField(mockTodo, "memo", null);

        // Service가 호출되면 위에서 만든 가짜 TodoResponse를 반환하도록 설정
        given(todoService.saveTodo(any(TodoSaveRequest.class), anyLong()))
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
                                fieldWithPath("categoryId").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                                fieldWithPath("date").type(JsonFieldType.STRING).description("투두 날짜 (YYYY-MM-DD, optional, 미래 날짜 가능, null이면 오늘 날짜)").optional()
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),

                                fieldWithPath("data.id").type(JsonFieldType.NUMBER).description("생성된 투두 ID"),
                                fieldWithPath("data.description").type(JsonFieldType.STRING).description("할 일 내용"),
                                fieldWithPath("data.status").type(JsonFieldType.STRING).description("상태 (IN_PROGRESS, COMPLETED, FAILED)"),
                                fieldWithPath("data.categoryId").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                                fieldWithPath("data.memo").type(JsonFieldType.STRING).description("메모").optional(),
                                fieldWithPath("data.date").type(JsonFieldType.STRING).description("투두 날짜"),

                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("투두 내용(제목) 수정 API")
    void updateDescription() throws Exception {
        // given
        Long todoId = 1L;
        String newDescription = "양파 다지기"; // 변경할 내용
        TodoDescriptionUpdateRequest request = new TodoDescriptionUpdateRequest(newDescription);

        Category mockCategory = Category.builder()
                .name("요리")
                .color(CategoryColor.BR)
                .userId(1L)
                .build();
        ReflectionTestUtils.setField(mockCategory, "id", 1L);

        Todo mockTodo = Todo.builder()
                .description(newDescription) // 변경된 내용 적용
                .category(mockCategory)
                .build();

        ReflectionTestUtils.setField(mockTodo, "id", todoId);
//        ReflectionTestUtils.setField(mockTodo, "status", Todo.Status.IN_PROGRESS);
//        ReflectionTestUtils.setField(mockTodo, "date", LocalDate.now());
//        ReflectionTestUtils.setField(mockTodo, "isBurnt", false);
//        ReflectionTestUtils.setField(mockTodo, "displayOrder", 1L);
//        ReflectionTestUtils.setField(mockTodo, "memo", "메모 있음");

        // Service Mocking
        given(todoService.updateDescription(anyLong(), anyLong(), any(TodoDescriptionUpdateRequest.class)))
                .willReturn(TodoResponse.from(mockTodo));

        // when & then
        mockMvc.perform(patch("/api/todos/{todoId}/description", todoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("todo-update-description",
                        pathParameters(
                                parameterWithName("todoId").description("수정할 투두 ID")
                        ),
                        requestFields(
                                fieldWithPath("description").type(JsonFieldType.STRING).description("변경할 할 일 내용")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),

                                fieldWithPath("data.id").type(JsonFieldType.NUMBER).description("투두 ID"),
                                fieldWithPath("data.description").type(JsonFieldType.STRING).description("변경된 내용"),
                                fieldWithPath("data.status").type(JsonFieldType.STRING).description("상태"),
                                fieldWithPath("data.categoryId").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                                fieldWithPath("data.memo").type(JsonFieldType.STRING).description("메모").optional(),
                                fieldWithPath("data.date").type(JsonFieldType.STRING).description("투두 날짜"),

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
                                fieldWithPath("data.memo").type(JsonFieldType.STRING).description("메모").optional(),
                                fieldWithPath("data.date").type(JsonFieldType.STRING).description("투두 날짜"),

                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("투두 메모 API")
    void updateMemo() throws Exception {
        // given
        Long todoId = 1L;
        String memoContent = "튀김옷 꼼꼼히 입히기";
        MemoRequest request = new MemoRequest(memoContent);

        MemoResponse mockResponse = MemoResponse.from(todoId, memoContent);

        // Mocking: Service 호출 시 위의 가짜 객체 반환
        given(todoService.updateMemo(anyLong(), anyLong(), any(MemoRequest.class)))
                .willReturn(mockResponse);

        // when & then
        mockMvc.perform(patch("/api/todos/{todoId}/memo", todoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("todo-update-memo",
                        pathParameters(
                                parameterWithName("todoId").description("메모를 수정할 투두 ID")
                        ),
                        requestFields(
                                fieldWithPath("memo").type(JsonFieldType.STRING).description("수정할 메모 내용")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),

                                fieldWithPath("data.todoId").type(JsonFieldType.NUMBER).description("투두 ID"),
                                fieldWithPath("data.memo").type(JsonFieldType.STRING).description("저장된 메모 내용"),

                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("투두 삭제 API")
    void deleteTodo() throws Exception {
        // given
        Long todoId = 1L;

        // when & then
        mockMvc.perform(delete("/api/todos/{todoId}", todoId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("todo-delete",
                        pathParameters(
                                parameterWithName("todoId").description("삭제할 투두 ID")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("반복 투두 전체 삭제 API")
    void deleteRecurrence() throws Exception {
        // given
        Long recurrenceId = 1L;

        // Mocking: void 메서드이므로 willDoNothing 사용
        willDoNothing().given(recurrenceService).deleteRecurrence(anyLong(), anyLong());

        // when & then
        mockMvc.perform(delete("/api/todos/recurrence/{recurrenceId}", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("todo-recurrence-delete",
                        pathParameters(
                                parameterWithName("recurrenceId").description("삭제할 반복 투두 규칙 ID (반복 규칙과 반복으로 생성된 모든 투두가 삭제됩니다)")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("투두 내일 하기 (날짜 미루기)")
    void postponeToTomorrow() throws Exception {
        // given
        Long todoId = 1L;
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        Category mockCategory = Category.builder().name("요리").color(CategoryColor.BR).userId(1L).build();
        ReflectionTestUtils.setField(mockCategory, "id", 1L);

        Todo mockTodo = Todo.builder()
                .description("양파 썰기")
                .category(mockCategory)
                .date(tomorrow)
                .build();
        ReflectionTestUtils.setField(mockTodo, "id", todoId);

        given(todoService.postponeToTomorrow(anyLong(), anyLong()))
                .willReturn(TodoResponse.from(mockTodo));

        // when & then
        mockMvc.perform(patch("/api/todos/{todoId}/tomorrow", todoId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("todo-postpone-tomorrow",
                        pathParameters(
                                parameterWithName("todoId").description("미룰 투두 ID")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),

                                fieldWithPath("data.id").type(JsonFieldType.NUMBER).description("투두 ID"),
                                fieldWithPath("data.description").type(JsonFieldType.STRING).description("할 일 내용"),
                                fieldWithPath("data.status").type(JsonFieldType.STRING).description("상태"),
                                fieldWithPath("data.categoryId").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                                fieldWithPath("data.memo").type(JsonFieldType.STRING).description("메모").optional(),

                                fieldWithPath("data.date").type(JsonFieldType.STRING).description("변경 후 날짜 (내일)"),

                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("투두 내일 하기 실패 - 오늘 날짜가 아닌 경우")
    void postponeToTomorrow_Fail_NotToday() throws Exception {
        // given
        Long todoId = 1L;

        // Mocking: Service가 예외를 던지도록 설정
        given(todoService.postponeToTomorrow(anyLong(), anyLong()))
                .willThrow(new BusinessException(ErrorCode.TODO_NOT_TODAY));

        // when & then
        mockMvc.perform(patch("/api/todos/{todoId}/tomorrow", todoId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("오늘 날짜의 투두만 내일로 미룰 수 있습니다."));
    }

    @Test
    @DisplayName("투두 오늘 하기 (날짜 당기기)")
    void moveToToday() throws Exception {
        // given
        Long todoId = 1L;
        LocalDate today = LocalDate.now();

        // Mocking: 날짜가 오늘로 변경된 투두 응답
        Category mockCategory = Category.builder().name("요리").color(CategoryColor.BR).userId(1L).build();
        ReflectionTestUtils.setField(mockCategory, "id", 1L);

        Todo mockTodo = Todo.builder()
                .description("양파 썰기")
                .category(mockCategory)
                .date(today)
                .build();
        ReflectionTestUtils.setField(mockTodo, "id", todoId);

        given(todoService.moveToToday(anyLong(), anyLong()))
                .willReturn(TodoResponse.from(mockTodo));

        // when & then
        mockMvc.perform(patch("/api/todos/{todoId}/today", todoId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("todo-move-today",
                        pathParameters(
                                parameterWithName("todoId").description("가져올 투두 ID")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),

                                fieldWithPath("data.id").type(JsonFieldType.NUMBER).description("투두 ID"),
                                fieldWithPath("data.description").type(JsonFieldType.STRING).description("할 일 내용"),
                                fieldWithPath("data.status").type(JsonFieldType.STRING).description("상태"),
                                fieldWithPath("data.categoryId").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                                fieldWithPath("data.memo").type(JsonFieldType.STRING).description("메모").optional(),

                                fieldWithPath("data.date").type(JsonFieldType.STRING).description("변경 후 날짜 (오늘)"),

                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("투두 날짜 변경")
    void updateTodoDate() throws Exception {
        // given
        Long todoId = 1L;
        LocalDate futureDate = LocalDate.now().plusDays(5); // 5일 뒤로 변경
        TodoDateUpdateRequest request = new TodoDateUpdateRequest(futureDate);

        // Mocking
        Category mockCategory = Category.builder().name("요리").color(CategoryColor.BR).userId(1L).build();
        ReflectionTestUtils.setField(mockCategory, "id", 1L);
        Todo mockTodo = Todo.builder()
                .description("장보기")
                .category(mockCategory)
                .date(futureDate) // 변경된 날짜
                .build();
        ReflectionTestUtils.setField(mockTodo, "id", todoId);

        given(todoService.updateTodoDate(anyLong(), anyLong(), any(TodoDateUpdateRequest.class)))
                .willReturn(TodoResponse.from(mockTodo));

        // when & then
        mockMvc.perform(patch("/api/todos/{todoId}/date", todoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("todo-update-date",
                        pathParameters(
                                parameterWithName("todoId").description("수정할 투두 ID (반복 투두인 경우 자동으로 분리됨)")
                        ),
                        requestFields(
                                fieldWithPath("date").type(JsonFieldType.STRING).description("변경할 날짜 (YYYY-MM-DD, 오늘 이후만 가능)")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                // data 필드 검증
                                fieldWithPath("data.id").type(JsonFieldType.NUMBER).description("투두 ID"),
                                fieldWithPath("data.description").type(JsonFieldType.STRING).description("할 일 내용"),
                                fieldWithPath("data.status").type(JsonFieldType.STRING).description("상태"),
                                fieldWithPath("data.categoryId").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                                fieldWithPath("data.memo").type(JsonFieldType.STRING).description("메모").optional(),
                                fieldWithPath("data.date").type(JsonFieldType.STRING).description("변경된 날짜"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("투두 카테고리 변경")
    void updateCategory() throws Exception {
        // given
        Long todoId = 1L;
        Long newCategoryId = 2L;
        TodoCategoryUpdateRequest request = new TodoCategoryUpdateRequest(newCategoryId);

        // Mocking
        Category oldCategory = Category.builder().name("요리").color(CategoryColor.BR).userId(1L).build();
        ReflectionTestUtils.setField(oldCategory, "id", 1L);

        Category newCategory = Category.builder().name("운동").color(CategoryColor.CB).userId(1L).build();
        ReflectionTestUtils.setField(newCategory, "id", newCategoryId);

        Todo mockTodo = Todo.builder()
                .description("장보기")
                .category(newCategory) // 변경된 카테고리
                .date(LocalDate.now())
                .build();
        ReflectionTestUtils.setField(mockTodo, "id", todoId);

        given(todoService.updateCategory(anyLong(), anyLong(), any(TodoCategoryUpdateRequest.class)))
                .willReturn(TodoResponse.from(mockTodo));

        // when & then
        mockMvc.perform(patch("/api/todos/{todoId}/category", todoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("todo-update-category",
                        pathParameters(
                                parameterWithName("todoId").description("카테고리를 변경할 투두 ID")
                        ),
                        requestFields(
                                fieldWithPath("categoryId").type(JsonFieldType.NUMBER).description("변경할 카테고리 ID")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("data.id").type(JsonFieldType.NUMBER).description("투두 ID"),
                                fieldWithPath("data.description").type(JsonFieldType.STRING).description("할 일 내용"),
                                fieldWithPath("data.status").type(JsonFieldType.STRING).description("상태"),
                                fieldWithPath("data.categoryId").type(JsonFieldType.NUMBER).description("변경된 카테고리 ID"),
                                fieldWithPath("data.memo").type(JsonFieldType.STRING).description("메모").optional(),
                                fieldWithPath("data.date").type(JsonFieldType.STRING).description("투두 날짜"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("투두 순서 변경")
    void reorderTodos() throws Exception {
        // given
        LocalDate targetDate = LocalDate.of(2025, 11, 26);
        List<Long> newOrderIds = List.of(3L, 1L, 2L); // 3번->1등, 1번->2등, 2번->3등
        OrderUpdateRequest request = new OrderUpdateRequest(newOrderIds);

        // when & then
        mockMvc.perform(patch("/api/todos/reorder")
                        .param("date", String.valueOf(targetDate))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("todo-reorder",
                        queryParameters(
                                parameterWithName("date").description("순서를 변경할 날짜 (YYYY-MM-DD)")
                        ),
                        requestFields(
                                fieldWithPath("ids").type(JsonFieldType.ARRAY).description("변경된 순서대로 나열된 투두 ID 리스트")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("투두 리스트 조회 API (전체/카테고리 필터)")
    void getTodoList() throws Exception {
        // given
        LocalDate date = LocalDate.of(2025, 11, 28);
        Long categoryId = 1L;

        Category mockCategory = Category.builder().name("운동").color(CategoryColor.BR).userId(1L).build();
        ReflectionTestUtils.setField(mockCategory, "id", categoryId);

        Todo todo1 = Todo.builder().description("스쿼트 100개").category(mockCategory).date(date).displayOrder(1L).build();
        ReflectionTestUtils.setField(todo1, "id", 100L);

        Todo todo2 = Todo.builder().description("런닝 30분").category(mockCategory).date(date).displayOrder(2L).build();
        ReflectionTestUtils.setField(todo2, "id", 101L);

        List<TodoListResponse> responses = List.of(
                TodoListResponse.from(todo1),
                TodoListResponse.from(todo2)
        );

        // Service Mocking (categoryId가 있을 때를 가정)
        given(todoService.getTodoList(anyLong(), any(LocalDate.class), anyLong()))
                .willReturn(responses);

        // when & then
        mockMvc.perform(get("/api/todos")
                        .param("date", date.toString())       // 필수 파라미터
                        .param("categoryId", String.valueOf(categoryId)) // 선택 파라미터
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("todo-list",
                        queryParameters(
                                parameterWithName("date").description("조회할 날짜 (YYYY-MM-DD)"),
                                parameterWithName("categoryId").description("필터링할 카테고리 ID (생략 시 전체 조회)").optional()
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),

                                fieldWithPath("data[].id").type(JsonFieldType.NUMBER).description("투두 ID (가상 회차는 null)"),
                                fieldWithPath("data[].description").type(JsonFieldType.STRING).description("할 일 내용"),
                                fieldWithPath("data[].status").type(JsonFieldType.STRING).description("상태 (IN_PROGRESS 등)"),
                                fieldWithPath("data[].categoryId").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                                fieldWithPath("data[].displayOrder").type(JsonFieldType.NUMBER).description("정렬 순서"),
                                fieldWithPath("data[].date").type(JsonFieldType.STRING).description("날짜"),
                                fieldWithPath("data[].recurrenceId").type(JsonFieldType.NUMBER).description("반복 투두 규칙 ID (일반 투두는 null)").optional(),
                                fieldWithPath("data[].occurrenceDate").type(JsonFieldType.STRING).description("가상 회차의 발생일 (일반 투두는 null)").optional(),

                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("일일 캐릭터 상태 조회 API")
    void getDailyCharacterStatus() throws Exception {
        // given
        LocalDate date = LocalDate.of(2025, 12, 1);

        // Mocking: CASE_D (절반 이상) 상태이며, 랜덤 이미지가 'd1_graphic'으로 결정된 상황 가정
        CharacterStatusResponse response = CharacterStatusResponse.builder()
                .status(CharacterStatus.CASE_D)
                .imageCode("d1_graphic")
                .description(CharacterStatus.CASE_D.getDescription())
                .build();

        given(todoService.getDailyCharacterStatus(anyLong(), any(LocalDate.class)))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/api/todos/character-status")
                        .param("date", date.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("todo-character-status",
                        queryParameters(
                                parameterWithName("date").description("조회할 날짜 (YYYY-MM-DD)")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),

                                fieldWithPath("data.status").type(JsonFieldType.STRING).description("캐릭터 상태 Enum (CASE_A ~ CASE_G)"),
                                fieldWithPath("data.imageCode").type(JsonFieldType.STRING).description("프론트엔드 이미지 매핑 코드 (예: '1', 'd1_graphic' 등)"),
                                fieldWithPath("data.description").type(JsonFieldType.STRING).description("상태 설명"),

                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("투두 반복 설정 API - WEEKLY (매주)")
    void createRecurringTodo() throws Exception {
        // given
        Long todoId = 1L;
        LocalDate startDate = LocalDate.of(2026, 1, 30);
        
        RecurrenceCreateRequest request = new RecurrenceCreateRequest();
        ReflectionTestUtils.setField(request, "todoId", todoId);
        ReflectionTestUtils.setField(request, "type", RecurrenceType.WEEKLY);
        ReflectionTestUtils.setField(request, "frequencyValues", List.of("MONDAY", "WEDNESDAY", "FRIDAY"));
        ReflectionTestUtils.setField(request, "startDate", startDate);
        ReflectionTestUtils.setField(request, "endDate", LocalDate.of(2026, 4, 25));
        ReflectionTestUtils.setField(request, "notificationTime", LocalTime.of(9, 0));

        // Mocking: 반복 설정 후 원본 투두 응답 생성
        Category mockCategory = Category.builder().name("요리").color(CategoryColor.BR).userId(1L).build();
        ReflectionTestUtils.setField(mockCategory, "id", 1L);

        Todo mockTodo = Todo.builder()
                .description("양파 썰기")
                .category(mockCategory)
                .date(startDate) // 시작일로 업데이트됨
                .build();
        ReflectionTestUtils.setField(mockTodo, "id", todoId);

        given(recurrenceService.createRecurrence(anyLong(), any(RecurrenceCreateRequest.class)))
                .willReturn(TodoResponse.from(mockTodo));

        // when & then
        mockMvc.perform(post("/api/todos/recurrence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("todo-recurrence-create-weekly",
                        requestFields(
                                fieldWithPath("todoId").type(JsonFieldType.NUMBER).description("반복 설정할 원본 투두 ID"),
                                fieldWithPath("type").type(JsonFieldType.STRING).description("반복 주기: WEEKLY (매주)"),
                                fieldWithPath("frequencyValues").type(JsonFieldType.ARRAY).description("반복 상세 값: 매주 발생할 요일 (예: [\"MONDAY\", \"WEDNESDAY\", \"FRIDAY\"] - 매주 월, 수, 금, [\"MONDAY\"] - 매주 월요일)"),
                                fieldWithPath("startDate").type(JsonFieldType.STRING).description("반복 시작일 (YYYY-MM-DD)"),
                                fieldWithPath("endDate").type(JsonFieldType.STRING).description("반복 종료일 (YYYY-MM-DD, null이면 무한 반복)").optional(),
                                fieldWithPath("notificationTime").type(JsonFieldType.STRING).description("알림 시간 (HH:mm:ss 형식)").optional()
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("data.id").type(JsonFieldType.NUMBER).description("원본 투두 ID"),
                                fieldWithPath("data.description").type(JsonFieldType.STRING).description("할 일 내용"),
                                fieldWithPath("data.status").type(JsonFieldType.STRING).description("상태 (IN_PROGRESS, COMPLETED)"),
                                fieldWithPath("data.categoryId").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                                fieldWithPath("data.memo").type(JsonFieldType.STRING).description("메모").optional(),
                                fieldWithPath("data.date").type(JsonFieldType.STRING).description("투두 날짜"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("투두 반복 설정 API - DAILY (매일)")
    void createRecurringTodoDaily() throws Exception {
        // given
        Long todoId = 1L;
        LocalDate startDate = LocalDate.of(2026, 1, 30);
        
        RecurrenceCreateRequest request = new RecurrenceCreateRequest();
        ReflectionTestUtils.setField(request, "todoId", todoId);
        ReflectionTestUtils.setField(request, "type", RecurrenceType.DAILY);
        ReflectionTestUtils.setField(request, "frequencyValues", null); // DAILY는 frequencyValues 불필요
        ReflectionTestUtils.setField(request, "startDate", startDate);
        ReflectionTestUtils.setField(request, "endDate", LocalDate.of(2026, 4, 25));
        ReflectionTestUtils.setField(request, "notificationTime", LocalTime.of(9, 0));

        // Mocking: 반복 설정 후 원본 투두 응답 생성
        Category mockCategory = Category.builder().name("운동").color(CategoryColor.BR).userId(1L).build();
        ReflectionTestUtils.setField(mockCategory, "id", 1L);

        Todo mockTodo = Todo.builder()
                .description("매일 운동하기")
                .category(mockCategory)
                .date(startDate)
                .build();
        ReflectionTestUtils.setField(mockTodo, "id", todoId);

        given(recurrenceService.createRecurrence(anyLong(), any(RecurrenceCreateRequest.class)))
                .willReturn(TodoResponse.from(mockTodo));

        // when & then
        mockMvc.perform(post("/api/todos/recurrence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("todo-recurrence-create-daily",
                        requestFields(
                                fieldWithPath("todoId").type(JsonFieldType.NUMBER).description("반복 설정할 원본 투두 ID"),
                                fieldWithPath("type").type(JsonFieldType.STRING).description("반복 주기: DAILY (매일)"),
                                fieldWithPath("frequencyValues").type(JsonFieldType.ARRAY).description("반복 상세 값 (DAILY는 null 또는 빈 배열)").optional(),
                                fieldWithPath("startDate").type(JsonFieldType.STRING).description("반복 시작일 (YYYY-MM-DD)"),
                                fieldWithPath("endDate").type(JsonFieldType.STRING).description("반복 종료일 (YYYY-MM-DD, null이면 무한 반복)").optional(),
                                fieldWithPath("notificationTime").type(JsonFieldType.STRING).description("알림 시간 (HH:mm:ss 형식)").optional()
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("data.id").type(JsonFieldType.NUMBER).description("원본 투두 ID"),
                                fieldWithPath("data.description").type(JsonFieldType.STRING).description("할 일 내용"),
                                fieldWithPath("data.status").type(JsonFieldType.STRING).description("상태 (IN_PROGRESS, COMPLETED)"),
                                fieldWithPath("data.categoryId").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                                fieldWithPath("data.memo").type(JsonFieldType.STRING).description("메모").optional(),
                                fieldWithPath("data.date").type(JsonFieldType.STRING).description("투두 날짜"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("투두 반복 설정 API - MONTHLY (매월)")
    void createRecurringTodoMonthly() throws Exception {
        // given
        Long todoId = 1L;
        LocalDate startDate = LocalDate.of(2026, 1, 25);
        
        RecurrenceCreateRequest request = new RecurrenceCreateRequest();
        ReflectionTestUtils.setField(request, "todoId", todoId);
        ReflectionTestUtils.setField(request, "type", RecurrenceType.MONTHLY);
        ReflectionTestUtils.setField(request, "frequencyValues", List.of("25"));
        ReflectionTestUtils.setField(request, "startDate", startDate);
        ReflectionTestUtils.setField(request, "endDate", null); // 무한 반복
        ReflectionTestUtils.setField(request, "notificationTime", LocalTime.of(9, 0));

        // Mocking: 반복 설정 후 원본 투두 응답 생성
        Category mockCategory = Category.builder().name("가계부").color(CategoryColor.BR).userId(1L).build();
        ReflectionTestUtils.setField(mockCategory, "id", 1L);

        Todo mockTodo = Todo.builder()
                .description("월세 납부")
                .category(mockCategory)
                .date(startDate)
                .build();
        ReflectionTestUtils.setField(mockTodo, "id", todoId);

        given(recurrenceService.createRecurrence(anyLong(), any(RecurrenceCreateRequest.class)))
                .willReturn(TodoResponse.from(mockTodo));

        // when & then
        mockMvc.perform(post("/api/todos/recurrence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("todo-recurrence-create-monthly",
                        requestFields(
                                fieldWithPath("todoId").type(JsonFieldType.NUMBER).description("반복 설정할 원본 투두 ID"),
                                fieldWithPath("type").type(JsonFieldType.STRING).description("반복 주기: MONTHLY (매월)"),
                                fieldWithPath("frequencyValues").type(JsonFieldType.ARRAY).description("반복 상세 값: 매월 발생할 날짜 숫자 (예: [\"7\"] - 매월 7일, [\"1\", \"15\"] - 매월 1일과 15일)"),
                                fieldWithPath("startDate").type(JsonFieldType.STRING).description("반복 시작일 (YYYY-MM-DD)"),
                                fieldWithPath("endDate").type(JsonFieldType.STRING).description("반복 종료일 (YYYY-MM-DD, null이면 무한 반복)").optional(),
                                fieldWithPath("notificationTime").type(JsonFieldType.STRING).description("알림 시간 (HH:mm:ss 형식)").optional()
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("data.id").type(JsonFieldType.NUMBER).description("원본 투두 ID"),
                                fieldWithPath("data.description").type(JsonFieldType.STRING).description("할 일 내용"),
                                fieldWithPath("data.status").type(JsonFieldType.STRING).description("상태 (IN_PROGRESS, COMPLETED)"),
                                fieldWithPath("data.categoryId").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                                fieldWithPath("data.memo").type(JsonFieldType.STRING).description("메모").optional(),
                                fieldWithPath("data.date").type(JsonFieldType.STRING).description("투두 날짜"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("투두 반복 설정 API - YEARLY (매년)")
    void createRecurringTodoYearly() throws Exception {
        // given
        Long todoId = 1L;
        LocalDate startDate = LocalDate.of(2026, 3, 30);
        
        RecurrenceCreateRequest request = new RecurrenceCreateRequest();
        ReflectionTestUtils.setField(request, "todoId", todoId);
        ReflectionTestUtils.setField(request, "type", RecurrenceType.YEARLY);
        ReflectionTestUtils.setField(request, "frequencyValues", List.of("03-30"));
        ReflectionTestUtils.setField(request, "startDate", startDate);
        ReflectionTestUtils.setField(request, "endDate", null); // 무한 반복
        ReflectionTestUtils.setField(request, "notificationTime", LocalTime.of(9, 0));

        // Mocking: 반복 설정 후 원본 투두 응답 생성
        Category mockCategory = Category.builder().name("기념일").color(CategoryColor.BR).userId(1L).build();
        ReflectionTestUtils.setField(mockCategory, "id", 1L);

        Todo mockTodo = Todo.builder()
                .description("매년 기념일")
                .category(mockCategory)
                .date(startDate)
                .build();
        ReflectionTestUtils.setField(mockTodo, "id", todoId);

        given(recurrenceService.createRecurrence(anyLong(), any(RecurrenceCreateRequest.class)))
                .willReturn(TodoResponse.from(mockTodo));

        // when & then
        mockMvc.perform(post("/api/todos/recurrence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("todo-recurrence-create-yearly",
                        requestFields(
                                fieldWithPath("todoId").type(JsonFieldType.NUMBER).description("반복 설정할 원본 투두 ID"),
                                fieldWithPath("type").type(JsonFieldType.STRING).description("반복 주기: YEARLY (매년)"),
                                fieldWithPath("frequencyValues").type(JsonFieldType.ARRAY).description("반복 상세 값: 매년 발생할 날짜 (MM-dd 형식, 예: [\"01-15\"] - 매년 1월 15일, [\"01-15\", \"12-25\"] - 매년 1월 15일과 12월 25일)"),
                                fieldWithPath("startDate").type(JsonFieldType.STRING).description("반복 시작일 (YYYY-MM-DD)"),
                                fieldWithPath("endDate").type(JsonFieldType.STRING).description("반복 종료일 (YYYY-MM-DD, null이면 무한 반복)").optional(),
                                fieldWithPath("notificationTime").type(JsonFieldType.STRING).description("알림 시간 (HH:mm:ss 형식)").optional()
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("data.id").type(JsonFieldType.NUMBER).description("원본 투두 ID"),
                                fieldWithPath("data.description").type(JsonFieldType.STRING).description("할 일 내용"),
                                fieldWithPath("data.status").type(JsonFieldType.STRING).description("상태 (IN_PROGRESS, COMPLETED)"),
                                fieldWithPath("data.categoryId").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                                fieldWithPath("data.memo").type(JsonFieldType.STRING).description("메모").optional(),
                                fieldWithPath("data.date").type(JsonFieldType.STRING).description("투두 날짜"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("투두 상세 조회 API")
    void getTodoDetail() throws Exception {
        // given
        Long todoId = 1L;

        // Mocking: 상세 조회 응답 데이터 생성
        TodoDetailResponse.TodoAlarmInfo alarmInfo = TodoDetailResponse.TodoAlarmInfo.builder()
                .alarmId(10L)
                .notifyAt(LocalDateTime.of(2026, 1, 8, 14, 30))
                .status("PENDING")
                .build();

        TodoDetailResponse.RecurrenceInfo recurrenceInfo = TodoDetailResponse.RecurrenceInfo.builder()
                .recurrenceId(20L)
                .type("WEEKLY")
                .frequencyValues("MONDAY,WEDNESDAY,FRIDAY")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .notificationTime(null) // DTO 로직상 null
                .build();

        TodoDetailResponse response = TodoDetailResponse.builder()
                .id(todoId)
                .description("투두 상세 내용 확인하기")
                .status("IN_PROGRESS")
                .categoryId(1L)
                .memo("꼼꼼하게 확인 필요")
                .date(LocalDate.of(2026, 1, 8))
                .alarm(alarmInfo)
                .recurrence(recurrenceInfo)
                .build();

        // Service Mocking
        given(todoService.getTodoDetail(anyLong(), anyLong()))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/api/todos/{todoId}", todoId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("todo-detail",
                        pathParameters(
                                parameterWithName("todoId").description("조회할 투두 ID")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),

                                // 기본 정보
                                fieldWithPath("data.id").type(JsonFieldType.NUMBER).description("투두 ID"),
                                fieldWithPath("data.description").type(JsonFieldType.STRING).description("할 일 내용"),
                                fieldWithPath("data.status").type(JsonFieldType.STRING).description("상태 (IN_PROGRESS, COMPLETED)"),
                                fieldWithPath("data.categoryId").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                                fieldWithPath("data.memo").type(JsonFieldType.STRING).description("메모").optional(),
                                fieldWithPath("data.date").type(JsonFieldType.STRING).description("날짜"),

                                // 알림 정보 (Optional)
                                fieldWithPath("data.alarm").type(JsonFieldType.OBJECT).description("알림 설정 정보").optional(),
                                fieldWithPath("data.alarm.alarmId").type(JsonFieldType.NUMBER).description("알림 ID").optional(),
                                fieldWithPath("data.alarm.notifyAt").type(JsonFieldType.STRING).description("알림 시간").optional(),
                                fieldWithPath("data.alarm.status").type(JsonFieldType.STRING).description("알림 상태(PENDING, SENT)").optional(),

                                // 반복 정보 (Optional)
                                fieldWithPath("data.recurrence").type(JsonFieldType.OBJECT).description("반복 설정 정보").optional(),
                                fieldWithPath("data.recurrence.recurrenceId").type(JsonFieldType.NUMBER).description("반복 규칙 ID").optional(),
                                fieldWithPath("data.recurrence.type").type(JsonFieldType.STRING).description("반복 주기").optional(),
                                fieldWithPath("data.recurrence.frequencyValues").type(JsonFieldType.STRING).description("반복 상세 값").optional(),
                                fieldWithPath("data.recurrence.startDate").type(JsonFieldType.STRING).description("반복 시작일").optional(),
                                fieldWithPath("data.recurrence.endDate").type(JsonFieldType.STRING).description("반복 종료일").optional(),
                                fieldWithPath("data.recurrence.notificationTime").type(JsonFieldType.STRING).description("반복 알림 시간").optional(),

                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }


    @Test
    @DisplayName("반복 투두 규칙 수정")
    void updateRecurrence() throws Exception {
        // given
        Long recurrenceId = 1L;
        LocalDate startDate = LocalDate.of(2026, 1, 30);
        
        RecurrenceUpdateRequest request = new RecurrenceUpdateRequest();
        ReflectionTestUtils.setField(request, "type", RecurrenceType.WEEKLY);
        ReflectionTestUtils.setField(request, "frequencyValues", List.of("TUESDAY", "THURSDAY"));
        ReflectionTestUtils.setField(request, "startDate", startDate);
        ReflectionTestUtils.setField(request, "endDate", LocalDate.of(2026, 5, 31));
        ReflectionTestUtils.setField(request, "notificationTime", LocalTime.of(10, 0));

        // Mocking: 수정된 Recurrence 엔티티 생성
        Recurrence mockRecurrence = Recurrence.builder()
                .userId(1L)
                .categoryId(1L)
                .description("매주 운동하기")
                .type(RecurrenceType.WEEKLY)
                .frequencyValues("TUESDAY,THURSDAY")
                .startDate(startDate)
                .endDate(LocalDate.of(2026, 1, 31))
                .notificationTime(LocalTime.of(10, 0))
                .lastGeneratedDate(startDate)
                .build();
        ReflectionTestUtils.setField(mockRecurrence, "id", recurrenceId);

        given(recurrenceService.updateRecurrence(anyLong(), any(RecurrenceUpdateRequest.class), anyLong()))
                .willReturn(mockRecurrence);

        // when & then
        mockMvc.perform(patch("/api/todos/recurrence/{recurrenceId}", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("todo-recurrence-update",
                        pathParameters(
                                parameterWithName("recurrenceId").description("수정할 반복 투두 규칙 ID")
                        ),
                        requestFields(
                                fieldWithPath("type").type(JsonFieldType.STRING).description("반복 주기 (DAILY, WEEKLY, MONTHLY, YEARLY)"),
                                fieldWithPath("frequencyValues").type(JsonFieldType.ARRAY).description("반복 상세 값 리스트 (요일, 날짜 등)").optional(),
                                fieldWithPath("startDate").type(JsonFieldType.STRING).description("반복 시작일 (YYYY-MM-DD)"),
                                fieldWithPath("endDate").type(JsonFieldType.STRING).description("반복 종료일 (YYYY-MM-DD, null이면 무한 반복)").optional(),
                                fieldWithPath("notificationTime").type(JsonFieldType.STRING).description("알림 시간 (HH:mm)").optional()
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("data.recurrenceId").type(JsonFieldType.NUMBER).description("반복 규칙 ID"),
                                fieldWithPath("data.type").type(JsonFieldType.STRING).description("반복 주기"),
                                fieldWithPath("data.frequencyValues").type(JsonFieldType.STRING).description("반복 상세 값").optional(),
                                fieldWithPath("data.startDate").type(JsonFieldType.STRING).description("반복 시작일"),
                                fieldWithPath("data.endDate").type(JsonFieldType.STRING).description("반복 종료일").optional(),
                                fieldWithPath("data.notificationTime").type(JsonFieldType.STRING).description("반복 알림 시간").optional(),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }
}