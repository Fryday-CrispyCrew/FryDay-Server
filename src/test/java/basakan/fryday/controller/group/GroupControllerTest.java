package basakan.fryday.controller.group;

import basakan.fryday.RestDocsSupport;
import basakan.fryday.common.config.SecurityConfig;
import basakan.fryday.common.security.JwtAuthenticationFilter;
import basakan.fryday.common.security.JwtTokenProvider;
import basakan.fryday.controller.group.request.GroupCreateRequest;
import basakan.fryday.controller.group.request.GroupNameUpdateRequest;
import basakan.fryday.controller.group.request.GroupPublicCategoryUpdateRequest;
import basakan.fryday.controller.group.response.GroupCreateResponse;
import basakan.fryday.controller.group.response.GroupDetailResponse;
import basakan.fryday.controller.group.response.GroupInvitePreviewResponse;
import basakan.fryday.controller.group.response.GroupListResponse;
import basakan.fryday.controller.group.response.GroupMemberResponse;
import basakan.fryday.controller.group.response.GroupNameResponse;
import basakan.fryday.controller.group.response.GroupPublicCategoryListResponse;
import basakan.fryday.controller.group.response.GroupPublicCategoryResponse;
import basakan.fryday.controller.group.response.GroupSummaryResponse;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.group.FryGroup;
import basakan.fryday.domain.group.GroupRole;
import basakan.fryday.service.group.GroupService;
import basakan.fryday.service.group.dto.GroupMemberDto;
import basakan.fryday.service.group.dto.GroupMemberTodoCountDto;
import basakan.fryday.service.group.dto.GroupSummaryDto;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = GroupController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
        }
)
@DisplayName("그룹 API")
class GroupControllerTest extends RestDocsSupport {

    private static final Long GROUP_ID = 1L;
    private static final Long OWNER_ID = 1L;
    private static final Long MEMBER_ID = 2L;

    @MockitoBean private GroupService groupService;
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUpSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(OWNER_ID, null, Collections.emptyList()));
    }

    @Test
    @DisplayName("그룹 생성 API")
    void createGroup() throws Exception {
        // given
        GroupCreateRequest request = new GroupCreateRequest("바삭한 사람들");
        given(groupService.createGroup(any(GroupCreateRequest.class), anyLong()))
                .willReturn(GroupCreateResponse.from(group()));

        // when & then
        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inviteCode").value("FRY123"))
                .andExpect(jsonPath("$.data.memberCount").value(1))
                .andDo(document("group-create",
                        requestFields(
                                fieldWithPath("name").type(JsonFieldType.STRING)
                                        .description("그룹 이름 (공백 제외 1~10자, 이모지 불가, 중복 허용)")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("data.groupId").type(JsonFieldType.NUMBER).description("생성된 그룹 ID"),
                                fieldWithPath("data.name").type(JsonFieldType.STRING).description("그룹 이름"),
                                fieldWithPath("data.inviteCode").type(JsonFieldType.STRING)
                                        .description("그룹 초대 코드 (영문 대문자 + 숫자 6자리)"),
                                fieldWithPath("data.memberCount").type(JsonFieldType.NUMBER)
                                        .description("현재 그룹원 수 (생성 직후에는 그룹장 1명)"),
                                fieldWithPath("data.maxMemberCount").type(JsonFieldType.NUMBER)
                                        .description("최대 그룹원 수"),
                                fieldWithPath("data.createdAt").type(JsonFieldType.STRING).description("그룹 생성 일시"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("그룹 이름이 비어 있으면 그룹을 만들 수 없다")
    void createGroupWithBlankNameFails() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GroupCreateRequest("   "))))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("내 그룹 목록 조회 API")
    void getMyGroups() throws Exception {
        // given
        given(groupService.getMyGroups(anyLong())).willReturn(groupList());

        // when & then
        mockMvc.perform(get("/api/groups"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groups[0].name").value("바삭한 사람들"))
                .andExpect(jsonPath("$.data.groups[0].myRole").value("OWNER"))
                .andExpect(jsonPath("$.data.groups[1].myRole").value("MEMBER"))
                .andDo(document("group-list",
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("data.groups[].groupId").type(JsonFieldType.NUMBER)
                                        .description("그룹 ID"),
                                fieldWithPath("data.groups[].name").type(JsonFieldType.STRING)
                                        .description("그룹 이름"),
                                fieldWithPath("data.groups[].memberCount").type(JsonFieldType.NUMBER)
                                        .description("현재 그룹원 수"),
                                fieldWithPath("data.groups[].maxMemberCount").type(JsonFieldType.NUMBER)
                                        .description("최대 그룹원 수"),
                                fieldWithPath("data.groups[].myRole").type(JsonFieldType.STRING)
                                        .description("내 권한 (OWNER: 그룹장, MEMBER: 그룹원)"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("초대 코드 조회 API")
    void getGroupByInviteCode() throws Exception {
        // given
        given(groupService.previewByInviteCode(any(String.class), anyLong()))
                .willReturn(GroupInvitePreviewResponse.of(group(), 3, false));

        // when & then
        mockMvc.perform(get("/api/groups/invite/{inviteCode}", "FRY123"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberCount").value(3))
                .andExpect(jsonPath("$.data.full").value(false))
                .andExpect(jsonPath("$.data.alreadyJoined").value(false))
                .andDo(document("group-invite-preview",
                        pathParameters(
                                parameterWithName("inviteCode").description("조회할 초대 코드 (대소문자 구분 없음)")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("data.groupId").type(JsonFieldType.NUMBER).description("그룹 ID"),
                                fieldWithPath("data.name").type(JsonFieldType.STRING).description("그룹 이름"),
                                fieldWithPath("data.memberCount").type(JsonFieldType.NUMBER)
                                        .description("현재 그룹원 수"),
                                fieldWithPath("data.maxMemberCount").type(JsonFieldType.NUMBER)
                                        .description("최대 그룹원 수"),
                                fieldWithPath("data.full").type(JsonFieldType.BOOLEAN)
                                        .description("정원이 가득 찼는지 여부. true 면 참여 버튼을 막아야 한다"),
                                fieldWithPath("data.alreadyJoined").type(JsonFieldType.BOOLEAN)
                                        .description("이미 참여 중인 그룹인지 여부"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("그룹 조회 API")
    void getGroup() throws Exception {
        // given
        given(groupService.getGroup(anyLong(), anyLong())).willReturn(groupDetail());

        // when & then
        mockMvc.perform(get("/api/groups/{groupId}", GROUP_ID))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myRole").value("OWNER"))
                .andExpect(jsonPath("$.data.members[0].role").value("OWNER"))
                .andExpect(jsonPath("$.data.members[1].totalCount").value(0))
                .andDo(document("group-detail",
                        pathParameters(
                                parameterWithName("groupId").description("조회할 그룹 ID")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("data.groupId").type(JsonFieldType.NUMBER).description("그룹 ID"),
                                fieldWithPath("data.name").type(JsonFieldType.STRING).description("그룹 이름"),
                                fieldWithPath("data.inviteCode").type(JsonFieldType.STRING).description("그룹 초대 코드"),
                                fieldWithPath("data.memberCount").type(JsonFieldType.NUMBER).description("현재 그룹원 수"),
                                fieldWithPath("data.maxMemberCount").type(JsonFieldType.NUMBER).description("최대 그룹원 수"),
                                fieldWithPath("data.myRole").type(JsonFieldType.STRING)
                                        .description("내 권한 (OWNER: 그룹장, MEMBER: 그룹원)"),
                                fieldWithPath("data.myPublicCategoryCount").type(JsonFieldType.NUMBER)
                                        .description("내가 이 그룹에 공개한 카테고리 수"),
                                fieldWithPath("data.date").type(JsonFieldType.STRING)
                                        .description("투두 집계 기준일 (Asia/Seoul 기준 오늘)"),
                                fieldWithPath("data.members[].userId").type(JsonFieldType.NUMBER)
                                        .description("그룹원 사용자 ID"),
                                fieldWithPath("data.members[].nickname").type(JsonFieldType.STRING)
                                        .description("그룹원 닉네임. 온보딩 중 닉네임 미설정 계정이면 null").optional(),
                                fieldWithPath("data.members[].role").type(JsonFieldType.STRING)
                                        .description("그룹원 권한 (OWNER: 그룹장, MEMBER: 그룹원). 그룹장이 항상 첫 번째"),
                                fieldWithPath("data.members[].totalCount").type(JsonFieldType.NUMBER)
                                        .description("공개된 카테고리 기준 오늘 전체 투두 개수"),
                                fieldWithPath("data.members[].completedCount").type(JsonFieldType.NUMBER)
                                        .description("공개된 카테고리 기준 오늘 완료한 투두 개수"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("그룹 이름 변경 API")
    void updateGroupName() throws Exception {
        // given
        FryGroup renamed = group();
        renamed.updateName("눅눅한 사람들");
        given(groupService.updateGroupName(anyLong(), anyLong(), any(GroupNameUpdateRequest.class)))
                .willReturn(GroupNameResponse.from(renamed));

        // when & then
        mockMvc.perform(patch("/api/groups/{groupId}/name", GROUP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GroupNameUpdateRequest("눅눅한 사람들"))))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("눅눅한 사람들"))
                .andDo(document("group-name-update",
                        pathParameters(
                                parameterWithName("groupId").description("이름을 변경할 그룹 ID")
                        ),
                        requestFields(
                                fieldWithPath("name").type(JsonFieldType.STRING)
                                        .description("변경할 그룹 이름 (공백 제외 1~10자, 이모지 불가)")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("data.groupId").type(JsonFieldType.NUMBER).description("그룹 ID"),
                                fieldWithPath("data.name").type(JsonFieldType.STRING).description("변경된 그룹 이름"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("그룹 해체 API")
    void deleteGroup() throws Exception {
        // given
        willDoNothing().given(groupService).deleteGroup(anyLong(), anyLong());

        // when & then
        mockMvc.perform(delete("/api/groups/{groupId}", GROUP_ID))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("group-delete",
                        pathParameters(
                                parameterWithName("groupId").description("해체할 그룹 ID (그룹장만 가능)")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("공개 카테고리 조회 API")
    void getPublicCategories() throws Exception {
        // given
        given(groupService.getPublicCategories(anyLong(), anyLong())).willReturn(publicCategories());

        // when & then
        mockMvc.perform(get("/api/groups/{groupId}/public-categories", GROUP_ID))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories[0].isPublic").value(true))
                .andExpect(jsonPath("$.data.categories[1].isPublic").value(false))
                .andDo(document("group-public-categories",
                        pathParameters(
                                parameterWithName("groupId").description("조회할 그룹 ID")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("data.categories[].categoryId").type(JsonFieldType.NUMBER)
                                        .description("카테고리 ID"),
                                fieldWithPath("data.categories[].name").type(JsonFieldType.STRING)
                                        .description("카테고리 이름"),
                                fieldWithPath("data.categories[].colorCode").type(JsonFieldType.STRING)
                                        .description("카테고리 색상 코드"),
                                fieldWithPath("data.categories[].colorHex").type(JsonFieldType.STRING)
                                        .description("카테고리 색상 헥사 코드"),
                                fieldWithPath("data.categories[].displayOrder").type(JsonFieldType.NUMBER)
                                        .description("카테고리 노출 순서"),
                                fieldWithPath("data.categories[].isPublic").type(JsonFieldType.BOOLEAN)
                                        .description("이 그룹에 공개 중인지 여부"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("공개 카테고리 변경 API")
    void updatePublicCategories() throws Exception {
        // given
        given(groupService.updatePublicCategories(anyLong(), anyLong(), any(GroupPublicCategoryUpdateRequest.class)))
                .willReturn(publicCategories());

        // when & then
        mockMvc.perform(patch("/api/groups/{groupId}/public-categories", GROUP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new GroupPublicCategoryUpdateRequest(List.of(10L)))))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("group-public-categories-update",
                        pathParameters(
                                parameterWithName("groupId").description("공개 카테고리를 변경할 그룹 ID")
                        ),
                        requestFields(
                                fieldWithPath("categoryIds").type(JsonFieldType.ARRAY)
                                        .description("공개할 카테고리 ID 목록. 기존 설정을 통째로 교체하며 최소 1개 이상이어야 한다")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("data.categories[].categoryId").type(JsonFieldType.NUMBER)
                                        .description("카테고리 ID"),
                                fieldWithPath("data.categories[].name").type(JsonFieldType.STRING)
                                        .description("카테고리 이름"),
                                fieldWithPath("data.categories[].colorCode").type(JsonFieldType.STRING)
                                        .description("카테고리 색상 코드"),
                                fieldWithPath("data.categories[].colorHex").type(JsonFieldType.STRING)
                                        .description("카테고리 색상 헥사 코드"),
                                fieldWithPath("data.categories[].displayOrder").type(JsonFieldType.NUMBER)
                                        .description("카테고리 노출 순서"),
                                fieldWithPath("data.categories[].isPublic").type(JsonFieldType.BOOLEAN)
                                        .description("변경 후 공개 여부"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("공개 카테고리를 하나도 선택하지 않으면 변경할 수 없다")
    void updatePublicCategoriesWithEmptyListFails() throws Exception {
        mockMvc.perform(patch("/api/groups/{groupId}/public-categories", GROUP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new GroupPublicCategoryUpdateRequest(List.of()))))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    private FryGroup group() {
        FryGroup group = FryGroup.builder()
                .name("바삭한 사람들").inviteCode("FRY123").ownerId(OWNER_ID).build();
        setField(group, "id", GROUP_ID);
        setField(group, "createdAt", LocalDateTime.of(2026, 9, 15, 12, 0, 0));
        return group;
    }

    private GroupDetailResponse groupDetail() {
        GroupMemberResponse owner = GroupMemberResponse.of(
                new GroupMemberDto(OWNER_ID, "연우", LocalDateTime.of(2026, 9, 15, 12, 0, 0)),
                GroupRole.OWNER,
                new GroupMemberTodoCountDto(OWNER_ID, 12, 5));
        GroupMemberResponse member = GroupMemberResponse.of(
                new GroupMemberDto(MEMBER_ID, "수정", LocalDateTime.of(2026, 9, 15, 13, 0, 0)),
                GroupRole.MEMBER,
                null);

        return GroupDetailResponse.of(group(), GroupRole.OWNER, 2,
                LocalDate.of(2026, 9, 15), List.of(owner, member));
    }

    private GroupListResponse groupList() {
        return GroupListResponse.from(List.of(
                GroupSummaryResponse.of(new GroupSummaryDto(GROUP_ID, "바삭한 사람들", OWNER_ID, 3), OWNER_ID),
                GroupSummaryResponse.of(new GroupSummaryDto(2L, "눅눅한 사람들", MEMBER_ID, 5), OWNER_ID)));
    }

    private GroupPublicCategoryListResponse publicCategories() {
        return GroupPublicCategoryListResponse.from(List.of(
                GroupPublicCategoryResponse.of(category(10L, "운동", CategoryColor.OR, 1L), true),
                GroupPublicCategoryResponse.of(category(11L, "공부", CategoryColor.BR, 2L), false)));
    }

    private Category category(Long id, String name, CategoryColor color, Long displayOrder) {
        Category category = Category.builder()
                .name(name).color(color).userId(OWNER_ID).displayOrder(displayOrder).build();
        setField(category, "id", id);
        return category;
    }
}
