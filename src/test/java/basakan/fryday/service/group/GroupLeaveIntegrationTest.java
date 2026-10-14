package basakan.fryday.service.group;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.group.request.GroupCreateRequest;
import basakan.fryday.controller.group.request.GroupJoinRequest;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:group_leave_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
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
@DisplayName("그룹 탈퇴")
class GroupLeaveIntegrationTest {

    private static final Long OWNER_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final Long OTHER_MEMBER_ID = 3L;
    private static final Long STRANGER_ID = 4L;

    @Autowired private GroupService groupService;
    @Autowired private FryGroupRepository fryGroupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private GroupPublicCategoryRepository groupPublicCategoryRepository;
    @Autowired private CategoryRepository categoryRepository;

    /** DB_CLOSE_DELAY=-1 + 롤백 없는 트랜잭션이라 테스트 간 데이터가 남는다. */
    @BeforeEach
    void clearGroupData() {
        groupPublicCategoryRepository.deleteAll();
        groupMemberRepository.deleteAll();
        fryGroupRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("탈퇴하면 내 그룹원 행과 공개 카테고리만 사라지고 다른 그룹원 것은 남는다")
    void leaveRemovesOnlyMyRows() {
        // given
        Long groupId = createGroup("바삭한 사람들");
        join(groupId, MEMBER_ID, "공부");
        join(groupId, OTHER_MEMBER_ID, "독서");

        assertThat(groupMemberRepository.countByGroupId(groupId)).isEqualTo(3);

        // when
        groupService.leaveGroup(groupId, MEMBER_ID);

        // then
        assertThat(groupMemberRepository.existsByGroupIdAndUserId(groupId, MEMBER_ID)).isFalse();
        assertThat(groupPublicCategoryRepository.countByGroupIdAndUserId(groupId, MEMBER_ID)).isZero();

        assertThat(groupMemberRepository.existsByGroupIdAndUserId(groupId, OTHER_MEMBER_ID)).isTrue();
        assertThat(groupPublicCategoryRepository.countByGroupIdAndUserId(groupId, OTHER_MEMBER_ID)).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("그룹원이 탈퇴해도 그룹과 그룹장은 그대로다")
    void leaveKeepsGroupAndOwner() {
        // given
        Long groupId = createGroup("바삭한 사람들");
        join(groupId, MEMBER_ID, "공부");

        // when
        groupService.leaveGroup(groupId, MEMBER_ID);

        // then
        assertThat(fryGroupRepository.findById(groupId)).isPresent();
        assertThat(groupMemberRepository.countByGroupId(groupId)).isEqualTo(1);
        assertThat(groupMemberRepository.existsByGroupIdAndUserId(groupId, OWNER_ID)).isTrue();
        assertThat(groupPublicCategoryRepository.countByGroupIdAndUserId(groupId, OWNER_ID)).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("그룹장은 탈퇴할 수 없다")
    void ownerCannotLeave() {
        // given
        Long groupId = createGroup("바삭한 사람들");
        join(groupId, MEMBER_ID, "공부");

        // when & then
        assertThatThrownBy(() -> groupService.leaveGroup(groupId, OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.GROUP_OWNER_CANNOT_LEAVE.getMessage());

        assertThat(groupMemberRepository.countByGroupId(groupId)).isEqualTo(2);
        assertThat(groupPublicCategoryRepository.countByGroupIdAndUserId(groupId, OWNER_ID)).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("그룹원이 아니면 탈퇴할 수 없다")
    void strangerCannotLeave() {
        // given
        Long groupId = createGroup("바삭한 사람들");

        // when & then
        assertThatThrownBy(() -> groupService.leaveGroup(groupId, STRANGER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.GROUP_NOT_FOUND.getMessage());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("탈퇴한 그룹에 같은 초대 코드로 다시 참여할 수 있다")
    void canRejoinAfterLeaving() {
        // given
        Long groupId = createGroup("바삭한 사람들");
        Category study = saveCategory(MEMBER_ID, "공부");
        groupService.join(new GroupJoinRequest(inviteCodeOf(groupId), List.of(study.getId())), MEMBER_ID);
        groupService.leaveGroup(groupId, MEMBER_ID);

        // when
        groupService.join(new GroupJoinRequest(inviteCodeOf(groupId), List.of(study.getId())), MEMBER_ID);

        // then
        assertThat(groupMemberRepository.existsByGroupIdAndUserId(groupId, MEMBER_ID)).isTrue();
        assertThat(groupPublicCategoryRepository.countByGroupIdAndUserId(groupId, MEMBER_ID)).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("다른 그룹의 내 데이터는 탈퇴에 영향받지 않는다")
    void leaveDoesNotTouchOtherGroups() {
        // given
        Long leftGroupId = createGroup("떠날 그룹");
        Long stayingGroupId = createGroup("남을 그룹");
        Category study = saveCategory(MEMBER_ID, "공부");
        groupService.join(new GroupJoinRequest(inviteCodeOf(leftGroupId), List.of(study.getId())), MEMBER_ID);
        groupService.join(new GroupJoinRequest(inviteCodeOf(stayingGroupId), List.of(study.getId())), MEMBER_ID);

        // when
        groupService.leaveGroup(leftGroupId, MEMBER_ID);

        // then
        assertThat(groupMemberRepository.existsByGroupIdAndUserId(stayingGroupId, MEMBER_ID)).isTrue();
        assertThat(groupPublicCategoryRepository.countByGroupIdAndUserId(stayingGroupId, MEMBER_ID)).isEqualTo(1);
        assertThat(categoryRepository.findById(study.getId())).isPresent();
    }

    private Long createGroup(String name) {
        saveCategory(OWNER_ID, "운동");
        return groupService.createGroup(new GroupCreateRequest(name), OWNER_ID).getGroupId();
    }

    private void join(Long groupId, Long userId, String categoryName) {
        Category category = saveCategory(userId, categoryName);
        groupService.join(new GroupJoinRequest(inviteCodeOf(groupId), List.of(category.getId())), userId);
    }

    private String inviteCodeOf(Long groupId) {
        return fryGroupRepository.findById(groupId).orElseThrow().getInviteCode();
    }

    private Category saveCategory(Long userId, String name) {
        return categoryRepository.saveAndFlush(Category.builder()
                .name(name).color(CategoryColor.OR).userId(userId).displayOrder(1L).build());
    }
}
