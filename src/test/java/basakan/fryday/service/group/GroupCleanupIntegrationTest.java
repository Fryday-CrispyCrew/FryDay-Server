package basakan.fryday.service.group;

import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.controller.group.request.GroupCreateRequest;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.group.GroupMember;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.group.FryGroupRepository;
import basakan.fryday.repository.group.GroupMemberRepository;
import basakan.fryday.repository.group.GroupPublicCategoryRepository;
import basakan.fryday.service.CategoryService;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 카테고리 삭제 / 회원 탈퇴가 그룹 데이터를 어떻게 정리하는지 검증한다.
 * 탈퇴 정리는 {@code UserWriteService.withdraw} 가 호출하는 {@code GroupService#leaveAllGroups} 를 직접 검증한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:group_cleanup_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        }
)
@Import({JpaConfig.class, GroupService.class, GroupCreator.class, InviteCodeGenerator.class, CategoryService.class})
@DisplayName("그룹 데이터 정리")
class GroupCleanupIntegrationTest {

    private static final Long OWNER_ID = 1L;
    private static final Long MEMBER_ID = 2L;

    @Autowired private GroupService groupService;
    @Autowired private CategoryService categoryService;
    @Autowired private FryGroupRepository fryGroupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private GroupPublicCategoryRepository groupPublicCategoryRepository;
    @Autowired private CategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        groupPublicCategoryRepository.deleteAll();
        groupMemberRepository.deleteAll();
        fryGroupRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("카테고리를 삭제하면 공개 중이던 그룹에서도 함께 제거된다")
    void deletingCategoryRemovesItFromGroups() {
        // given
        Category exercise = saveCategory(OWNER_ID, "운동");
        Category study = saveCategory(OWNER_ID, "공부");
        Long groupId = createGroup("바삭한 사람들", OWNER_ID);
        assertThat(groupPublicCategoryRepository.countByGroupIdAndUserId(groupId, OWNER_ID)).isEqualTo(2);

        // when
        categoryService.deleteCategory(exercise.getId(), OWNER_ID);

        // then
        assertThat(groupPublicCategoryRepository.findAllByGroupIdAndUserId(groupId, OWNER_ID))
                .extracting(gpc -> gpc.getCategoryId())
                .containsExactly(study.getId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("카테고리 삭제는 다른 그룹의 공개 설정에도 반영된다")
    void deletingCategoryRemovesItFromEveryGroup() {
        // given
        Category exercise = saveCategory(OWNER_ID, "운동");
        Long firstGroupId = createGroup("첫 번째 그룹", OWNER_ID);
        Long secondGroupId = createGroup("두 번째 그룹", OWNER_ID);

        // when
        categoryService.deleteCategory(exercise.getId(), OWNER_ID);

        // then
        assertThat(groupPublicCategoryRepository.countByGroupIdAndUserId(firstGroupId, OWNER_ID)).isZero();
        assertThat(groupPublicCategoryRepository.countByGroupIdAndUserId(secondGroupId, OWNER_ID)).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("그룹장이 탈퇴하면 그 그룹은 해체되고, 그룹원으로 있던 그룹에서는 본인만 빠진다")
    void withdrawingOwnerDisbandsOwnedGroupAndLeavesOthers() {
        // given
        saveCategory(OWNER_ID, "운동");
        saveCategory(MEMBER_ID, "공부");

        Long ownedGroupId = createGroup("내가 만든 그룹", OWNER_ID);
        groupMemberRepository.saveAndFlush(
                GroupMember.builder().groupId(ownedGroupId).userId(MEMBER_ID).build());

        Long joinedGroupId = createGroup("남이 만든 그룹", MEMBER_ID);
        groupMemberRepository.saveAndFlush(
                GroupMember.builder().groupId(joinedGroupId).userId(OWNER_ID).build());
        groupService.updatePublicCategories(joinedGroupId, OWNER_ID,
                new basakan.fryday.controller.group.request.GroupPublicCategoryUpdateRequest(
                        java.util.List.of(categoryRepository
                                .findAllByUserIdAndDeletedAtIsNullOrderByDisplayOrderAsc(OWNER_ID)
                                .get(0).getId())));

        // when — UserWriteService.withdraw 가 호출하는 정리 메서드
        groupService.leaveAllGroups(OWNER_ID);

        // then
        assertThat(fryGroupRepository.findById(ownedGroupId)).isEmpty();
        assertThat(groupMemberRepository.countByGroupId(ownedGroupId)).isZero();

        assertThat(fryGroupRepository.findById(joinedGroupId)).isPresent();
        assertThat(groupMemberRepository.countByGroupId(joinedGroupId)).isEqualTo(1);
        assertThat(groupMemberRepository.existsByGroupIdAndUserId(joinedGroupId, OWNER_ID)).isFalse();
        assertThat(groupPublicCategoryRepository.countByGroupIdAndUserId(joinedGroupId, OWNER_ID)).isZero();
        assertThat(groupPublicCategoryRepository.countByGroupIdAndUserId(joinedGroupId, MEMBER_ID)).isEqualTo(1);
    }

    private Long createGroup(String name, Long userId) {
        return groupService.createGroup(new GroupCreateRequest(name), userId).getGroupId();
    }

    private Category saveCategory(Long userId, String name) {
        return categoryRepository.saveAndFlush(Category.builder()
                .name(name).color(CategoryColor.OR).userId(userId).displayOrder(1L).build());
    }
}
