package basakan.fryday.controller;

import basakan.fryday.RestDocsSupport;
import basakan.fryday.controller.category.CategoryController;
import basakan.fryday.controller.category.request.CategoryCreateRequest;
import basakan.fryday.controller.category.request.CategoryUpdateRequest;
import basakan.fryday.controller.dto.OrderUpdateRequest;
import basakan.fryday.controller.category.response.CategoryReadResponse;
import basakan.fryday.controller.category.response.CategoryResponse;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.todo.CategoryColor;
import basakan.fryday.service.CategoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.test.util.ReflectionTestUtils.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
class CategoryControllerTest extends RestDocsSupport {

    @MockitoBean
    private CategoryService categoryService;

    @Test
    @DisplayName("카테고리 생성 API")
    void createCategory() throws Exception{
        // given
        CategoryCreateRequest request = new CategoryCreateRequest("운동", CategoryColor.BR, 1L);

        Category mockCategory = Category.builder()
                .name("운동")
                .color(CategoryColor.BR)
                .userId(1L)
                .build();
        // ID는 DB 저장 시 생성되므로, 리플렉션으로 강제 주입 (테스트니까요)
        setField(mockCategory, "id", 1L);

        given(categoryService.createCategory(any(CategoryCreateRequest.class)))
                .willReturn(CategoryResponse.from(mockCategory));

        // when & then
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("category-create",
                        requestFields(
                                fieldWithPath("name").type(JsonFieldType.STRING).description("카테고리 이름 (최대 8자)"),
                                fieldWithPath("color").type(JsonFieldType.STRING).description("카테고리 색상 코드"),
                                fieldWithPath("userId").type(JsonFieldType.NUMBER).description("사용자 ID")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),

                                // ⭐️ data 필드 하위 내용 상세 정의
                                fieldWithPath("data.id").type(JsonFieldType.NUMBER).description("생성된 카테고리 ID"),
                                fieldWithPath("data.name").type(JsonFieldType.STRING).description("카테고리 이름"),
                                fieldWithPath("data.color").type(JsonFieldType.STRING).description("카테고리 색상"),

                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("카테고리 수정 API")
    void updateCategory() throws Exception {
        // given
        Long categoryId = 1L;
        CategoryUpdateRequest request = new CategoryUpdateRequest("독서", CategoryColor.YL);

        given(categoryService.updateCategory(any(), any(), any()))
                .willReturn(new CategoryResponse(
                        Category.builder()
                                .name("독서")           // 수정된 이름
                                .color(CategoryColor.YL) // 수정된 색상
                                .userId(1L)
                                .build()
                ));

        // when & then
        mockMvc.perform(patch("/api/categories/{categoryId}", categoryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("category-update",
                        pathParameters(
                                parameterWithName("categoryId").description("수정할 카테고리 ID")
                        ),
                        requestFields(
                                fieldWithPath("name").type(JsonFieldType.STRING).description("변경할 카테고리 이름"),
                                fieldWithPath("color").type(JsonFieldType.STRING).description("변경할 카테고리 색상 코드")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),

                                fieldWithPath("data.id").type(JsonFieldType.NULL).description("카테고리 ID (Mock이라 null일 수 있음)"),
                                // (빌더로 만들 때 id를 세팅 안해서 null로 나올 수 있음. Mock 객체 만들 때 ReflectionTestUtils로 ID 넣거나 하면 됨)
                                fieldWithPath("data.name").type(JsonFieldType.STRING).description("수정된 이름"),
                                fieldWithPath("data.color").type(JsonFieldType.STRING).description("수정된 색상"),

                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("카테고리 삭제 API")
    void deleteCategory() throws Exception {
        // given
        Long categoryId = 1L;

        // when & then
        mockMvc.perform(delete("/api/categories/{categoryId}", categoryId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("category-delete",
                        pathParameters(
                                parameterWithName("categoryId").description("삭제할 카테고리 ID")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("data").type(JsonFieldType.NULL).description("데이터 (없음)").optional(),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("카테고리 순서 변경")
    void reorderCategories() throws Exception {
        // given
        List<Long> newOrderIds = List.of(3L, 1L, 2L);
        OrderUpdateRequest request = new OrderUpdateRequest(newOrderIds);

        // when & then
        mockMvc.perform(patch("/api/categories/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("category-reorder",
                        requestFields(
                                fieldWithPath("ids").type(JsonFieldType.ARRAY).description("변경할 순서대로 나열된 카테고리 ID 리스트")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("카테고리 목록 조회 API")
    void getCategories() throws Exception {
        // given
        Category cat1 = Category.builder().name("운동").color(CategoryColor.BR).userId(1L).displayOrder(1L).build();
        ReflectionTestUtils.setField(cat1, "id", 10L);

        Category cat2 = Category.builder().name("요리").color(CategoryColor.YL).userId(1L).displayOrder(2L).build();
        ReflectionTestUtils.setField(cat2, "id", 11L);

        List<CategoryReadResponse> responses = List.of(
                CategoryReadResponse.from(cat1),
                CategoryReadResponse.from(cat2)
        );

        given(categoryService.getCategoriesByUserId(anyLong()))
                .willReturn(responses);

        // when & then
        mockMvc.perform(get("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("category-list",

                        // 응답 필드
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),

                                fieldWithPath("data[].id").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                                fieldWithPath("data[].name").type(JsonFieldType.STRING).description("카테고리 이름"),
                                fieldWithPath("data[].colorCode").type(JsonFieldType.STRING).description("색상 코드 (로직용)"),
                                fieldWithPath("data[].colorHex").type(JsonFieldType.STRING).description("색상 헥사코드 (화면 표시용)"),
                                fieldWithPath("data[].displayOrder").type(JsonFieldType.NUMBER).description("정렬 순서"),

                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }
}