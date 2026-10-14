package basakan.fryday.service.group;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.group.request.GroupCreateRequest;
import basakan.fryday.controller.group.request.GroupPublicCategoryUpdateRequest;
import basakan.fryday.controller.group.response.GroupPublicCategoryListResponse;
import basakan.fryday.controller.group.response.GroupPublicCategoryResponse;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.group.FryGroupRepository;
import basakan.fryday.repository.group.GroupMemberRepository;
import basakan.fryday.repository.group.GroupPublicCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:group_public_category_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        }
)
@Import({JpaConfig.class, GroupService.class, GroupCreator.class, InviteCodeGenerator.class})
@DisplayName("그룹 공개 카테고리")
class GroupPublicCategoryIntegrationTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long STRANGER_ID = 3L;

    @Autowired private GroupService groupService;
    @Autowired private FryGroupRepository fryGroupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private GroupPublicCategoryRepository groupPublicCategoryRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Long groupId;
    private Category exercise;
    private Category study;
    private Category hobby;

    @BeforeEach
    void setUp() {
        groupPublicCategoryRepository.deleteAll();
        groupMemberRepository.deleteAll();
        fryGroupRepository.deleteAll();
        categoryRepository.deleteAll();

        exercise = saveCategory(OWNER_ID, "운동", 1L);
        study = saveCategory(OWNER_ID, "공부", 2L);
        hobby = saveCategory(OWNER_ID, "취미", 3L);

        groupId = groupService.createGroup(new GroupCreateRequest("바삭한 사람들"), OWNER_ID).getGroupId();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("그룹을 만든 직후에는 내 카테고리가 모두 공개 상태다")
    void allCategoriesArePublicRightAfterCreation() {
        // when
        GroupPublicCategoryListResponse response = groupService.getPublicCategories(groupId, OWNER_ID);

        // then
        assertThat(response.getCategories())
                .extracting(GroupPublicCategoryResponse::getName, GroupPublicCategoryResponse::getIsPublic)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("운동", true),
                        org.assertj.core.groups.Tuple.tuple("공부", true),
                        org.assertj.core.groups.Tuple.tuple("취미", true));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("공개 카테고리를 수정하면 통째로 교체된다")
    void updateReplacesAllPublicCategories() {
        // when
        GroupPublicCategoryListResponse response = groupService.updatePublicCategories(
                groupId, OWNER_ID, new GroupPublicCategoryUpdateRequest(List.of(study.getId())));

        // then
        assertThat(response.getCategories())
                .filteredOn(GroupPublicCategoryResponse::getIsPublic)
                .extracting(GroupPublicCategoryResponse::getName)
                .containsExactly("공부");
        assertThat(groupPublicCategoryRepository.countByGroupIdAndUserId(groupId, OWNER_ID)).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("공개하지 않은 카테고리도 목록에는 나오되 비공개로 표시된다")
    void nonPublicCategoriesAreStillListed() {
        // given
        groupService.updatePublicCategories(
                groupId, OWNER_ID, new GroupPublicCategoryUpdateRequest(List.of(exercise.getId())));

        // when
        GroupPublicCategoryListResponse response = groupService.getPublicCategories(groupId, OWNER_ID);

        // then
        assertThat(response.getCategories()).hasSize(3);
        assertThat(response.getCategories())
                .filteredOn(category -> !category.getIsPublic())
                .extracting(GroupPublicCategoryResponse::getName)
                .containsExactly("공부", "취미");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("삭제된 카테고리는 목록에 나오지 않는다")
    void deletedCategoryIsNotListed() {
        // given
        hobby.delete();
        categoryRepository.saveAndFlush(hobby);

        // when
        GroupPublicCategoryListResponse response = groupService.getPublicCategories(groupId, OWNER_ID);

        // then
        assertThat(response.getCategories())
                .extracting(GroupPublicCategoryResponse::getName)
                .containsExactly("운동", "공부");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("공개 카테고리를 하나도 선택하지 않으면 수정할 수 없다")
    void rejectsEmptySelection() {
        assertThatThrownBy(() -> groupService.updatePublicCategories(
                groupId, OWNER_ID, new GroupPublicCategoryUpdateRequest(List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.PUBLIC_CATEGORY_REQUIRED.getMessage());

        assertThat(groupPublicCategoryRepository.countByGroupIdAndUserId(groupId, OWNER_ID)).isEqualTo(3);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("남의 카테고리는 공개할 수 없다")
    void rejectsOtherUsersCategory() {
        // given
        Category othersCategory = saveCategory(OTHER_USER_ID, "남의 카테고리", 1L);

        // when & then
        assertThatThrownBy(() -> groupService.updatePublicCategories(
                groupId, OWNER_ID, new GroupPublicCategoryUpdateRequest(List.of(othersCategory.getId()))))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.CATEGORY_NOT_FOUND.getMessage());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("삭제된 카테고리는 공개할 수 없다")
    void rejectsDeletedCategory() {
        // given
        hobby.delete();
        categoryRepository.saveAndFlush(hobby);

        // when & then
        assertThatThrownBy(() -> groupService.updatePublicCategories(
                groupId, OWNER_ID, new GroupPublicCategoryUpdateRequest(List.of(hobby.getId()))))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.CATEGORY_NOT_FOUND.getMessage());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("그룹원이 아니면 공개 카테고리를 조회하거나 수정할 수 없다")
    void strangerCannotTouchPublicCategories() {
        assertThatThrownBy(() -> groupService.getPublicCategories(groupId, STRANGER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.GROUP_NOT_FOUND.getMessage());

        assertThatThrownBy(() -> groupService.updatePublicCategories(
                groupId, STRANGER_ID, new GroupPublicCategoryUpdateRequest(List.of(exercise.getId()))))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.GROUP_NOT_FOUND.getMessage());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("같은 카테고리를 여러 번 보내도 한 번만 공개된다")
    void deduplicatesRepeatedCategoryIds() {
        // when
        groupService.updatePublicCategories(groupId, OWNER_ID,
                new GroupPublicCategoryUpdateRequest(List.of(
                        exercise.getId(), exercise.getId(), exercise.getId())));

        // then
        assertThat(groupPublicCategoryRepository.countByGroupIdAndUserId(groupId, OWNER_ID)).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("내 카테고리를 중복시켜 개수를 맞춰도 남의 카테고리는 공개되지 않는다")
    void cannotSmuggleOthersCategoryByPaddingWithDuplicates() {
        // given — 개수 대조를 속이려는 시도: 내 것 2번 + 남의 것 1번
        Category othersCategory = saveCategory(OTHER_USER_ID, "남의 카테고리", 1L);

        // when & then
        assertThatThrownBy(() -> groupService.updatePublicCategories(groupId, OWNER_ID,
                new GroupPublicCategoryUpdateRequest(List.of(
                        exercise.getId(), exercise.getId(), othersCategory.getId()))))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.CATEGORY_NOT_FOUND.getMessage());

        // 실패했으니 기존 공개 설정이 그대로 남아야 한다
        assertThat(groupPublicCategoryRepository.countByGroupIdAndUserId(groupId, OWNER_ID)).isEqualTo(3);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("존재하지 않는 카테고리 id 나 null 이 섞여도 서버 오류 없이 거부된다")
    void rejectsUnknownOrNullCategoryId() {
        assertThatThrownBy(() -> groupService.updatePublicCategories(groupId, OWNER_ID,
                new GroupPublicCategoryUpdateRequest(Arrays.asList(exercise.getId(), 999_999L))))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.CATEGORY_NOT_FOUND.getMessage());

        assertThatThrownBy(() -> groupService.updatePublicCategories(groupId, OWNER_ID,
                new GroupPublicCategoryUpdateRequest(Arrays.asList(exercise.getId(), null))))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.CATEGORY_NOT_FOUND.getMessage());
    }

    private Category saveCategory(Long userId, String name, Long displayOrder) {
        return categoryRepository.saveAndFlush(Category.builder()
                .name(name).color(CategoryColor.OR).userId(userId).displayOrder(displayOrder).build());
    }
}
