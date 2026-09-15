package basakan.fryday.service.group;

import basakan.fryday.controller.group.response.GroupCreateResponse;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.group.FryGroup;
import basakan.fryday.domain.group.GroupMember;
import basakan.fryday.domain.group.GroupPublicCategory;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.group.FryGroupRepository;
import basakan.fryday.repository.group.GroupMemberRepository;
import basakan.fryday.repository.group.GroupPublicCategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("그룹 생성 트랜잭션")
class GroupCreatorTest {

    private static final Long OWNER_ID = 1L;
    private static final Long GROUP_ID = 100L;

    @Mock private FryGroupRepository fryGroupRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private GroupPublicCategoryRepository groupPublicCategoryRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private InviteCodeGenerator inviteCodeGenerator;

    @InjectMocks private GroupCreator groupCreator;

    @Captor private ArgumentCaptor<FryGroup> groupCaptor;
    @Captor private ArgumentCaptor<GroupMember> memberCaptor;
    @Captor private ArgumentCaptor<List<GroupPublicCategory>> publicCategoriesCaptor;

    @Test
    @DisplayName("만든 사람이 그룹장이 되고 초대 코드가 즉시 발급된다")
    void createsGroupWithOwnerAndInviteCode() {
        // given
        given(inviteCodeGenerator.generate()).willReturn("FRY123");
        given(categoryRepository.findAllByUserIdAndDeletedAtIsNullOrderByDisplayOrderAsc(OWNER_ID))
                .willReturn(List.of());
        givenGroupIsSaved();

        // when
        GroupCreateResponse response = groupCreator.create("바삭한 사람들", OWNER_ID);

        // then
        assertThat(response.getGroupId()).isEqualTo(GROUP_ID);
        assertThat(response.getName()).isEqualTo("바삭한 사람들");
        assertThat(response.getInviteCode()).isEqualTo("FRY123");
        assertThat(response.getMemberCount()).isEqualTo(1);
        assertThat(response.getMaxMemberCount()).isEqualTo(FryGroup.MAX_MEMBER_COUNT);

        then(fryGroupRepository).should().saveAndFlush(groupCaptor.capture());
        assertThat(groupCaptor.getValue().getOwnerId()).isEqualTo(OWNER_ID);

        then(groupMemberRepository).should().save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getUserId()).isEqualTo(OWNER_ID);
        assertThat(memberCaptor.getValue().getGroupId()).isEqualTo(GROUP_ID);
    }

    @Test
    @DisplayName("내 카테고리가 모두 공개 상태로 등록된다")
    void publishesAllMyCategories() {
        // given
        given(inviteCodeGenerator.generate()).willReturn("FRY123");
        given(categoryRepository.findAllByUserIdAndDeletedAtIsNullOrderByDisplayOrderAsc(OWNER_ID))
                .willReturn(List.of(category(10L), category(11L)));
        givenGroupIsSaved();

        // when
        groupCreator.create("바삭한 사람들", OWNER_ID);

        // then
        then(groupPublicCategoryRepository).should().saveAll(publicCategoriesCaptor.capture());
        assertThat(publicCategoriesCaptor.getValue())
                .extracting(GroupPublicCategory::getCategoryId)
                .containsExactly(10L, 11L);
        assertThat(publicCategoriesCaptor.getValue()).allSatisfy(publicCategory -> {
            assertThat(publicCategory.getGroupId()).isEqualTo(GROUP_ID);
            assertThat(publicCategory.getUserId()).isEqualTo(OWNER_ID);
        });
    }

    private void givenGroupIsSaved() {
        given(fryGroupRepository.saveAndFlush(any(FryGroup.class))).willAnswer(invocation -> {
            FryGroup saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", GROUP_ID);
            return saved;
        });
    }

    private Category category(Long id) {
        Category category = Category.builder()
                .name("카테고리").color(CategoryColor.OR).userId(OWNER_ID).displayOrder(1L).build();
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}
