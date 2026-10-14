package basakan.fryday.service.group;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.group.request.GroupCreateRequest;
import basakan.fryday.controller.group.request.GroupJoinRequest;
import basakan.fryday.controller.group.response.GroupInvitePreviewResponse;
import basakan.fryday.controller.group.response.GroupJoinResponse;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.group.FryGroup;
import basakan.fryday.domain.group.GroupMember;
import basakan.fryday.domain.group.GroupPublicCategory;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.auth.UserJpaRepository;
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
                "spring.datasource.url=jdbc:h2:mem:group_invite_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
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
@DisplayName("초대 코드")
class GroupInviteIntegrationTest {

    @Autowired private GroupService groupService;
    @Autowired private FryGroupRepository fryGroupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private GroupPublicCategoryRepository groupPublicCategoryRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserJpaRepository userJpaRepository;

    private Long ownerId;
    private Long joinerId;

    @BeforeEach
    void setUp() {
        groupPublicCategoryRepository.deleteAll();
        groupMemberRepository.deleteAll();
        fryGroupRepository.deleteAll();
        categoryRepository.deleteAll();
        userJpaRepository.deleteAll();

        ownerId = saveUser("owner", "연우");
        joinerId = saveUser("joiner", "수정");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("초대 코드로 참여하면 그룹원과 선택한 공개 카테고리가 함께 등록된다")
    void joinRegistersMemberAndSelectedCategories() {
        // given
        saveCategory(ownerId, "운동");
        Long groupId = createGroup("바삭한 사람들", ownerId);

        Category study = saveCategory(joinerId, "공부");
        Category hidden = saveCategory(joinerId, "비밀");

        // when — 공부만 공개하고 참여한다
        GroupJoinResponse response = groupService.join(
                new GroupJoinRequest(inviteCodeOf(groupId), List.of(study.getId())), joinerId);

        // then
        assertThat(response.getGroupId()).isEqualTo(groupId);
        assertThat(response.getName()).isEqualTo("바삭한 사람들");
        assertThat(response.getMemberCount()).isEqualTo(2);
        assertThat(response.getMaxMemberCount()).isEqualTo(FryGroup.MAX_MEMBER_COUNT);

        assertThat(groupMemberRepository.existsByGroupIdAndUserId(groupId, joinerId)).isTrue();
        assertThat(groupPublicCategoryRepository.findAllByGroupIdAndUserId(groupId, joinerId))
                .extracting(GroupPublicCategory::getCategoryId)
                .containsExactly(study.getId())
                .doesNotContain(hidden.getId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("초대 코드는 대소문자를 가리지 않는다")
    void joinAcceptsLowercaseInviteCode() {
        // given
        saveCategory(ownerId, "운동");
        Long groupId = createGroup("바삭한 사람들", ownerId);
        Category study = saveCategory(joinerId, "공부");

        // when
        GroupJoinResponse response = groupService.join(
                new GroupJoinRequest(inviteCodeOf(groupId).toLowerCase(), List.of(study.getId())), joinerId);

        // then
        assertThat(response.getGroupId()).isEqualTo(groupId);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("존재하지 않는 초대 코드로는 참여할 수 없다")
    void joinRejectsUnknownInviteCode() {
        // given
        Category study = saveCategory(joinerId, "공부");

        // when & then
        assertThatThrownBy(() -> groupService.join(new GroupJoinRequest("ZZZZZZ", List.of(study.getId())), joinerId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVITE_CODE_NOT_FOUND.getMessage());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("이미 참여 중인 그룹에는 다시 참여할 수 없다")
    void joinRejectsDuplicateMember() {
        // given
        saveCategory(ownerId, "운동");
        Long groupId = createGroup("바삭한 사람들", ownerId);
        Category study = saveCategory(joinerId, "공부");
        groupService.join(new GroupJoinRequest(inviteCodeOf(groupId), List.of(study.getId())), joinerId);

        // when & then
        assertThatThrownBy(() -> groupService.join(
                new GroupJoinRequest(inviteCodeOf(groupId), List.of(study.getId())), joinerId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.GROUP_ALREADY_JOINED.getMessage());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("정원이 찬 그룹에는 참여할 수 없다")
    void joinRejectsFullGroup() {
        // given — 그룹장 포함 10명을 채운다
        saveCategory(ownerId, "운동");
        Long groupId = createGroup("바삭한 사람들", ownerId);
        for (int i = 0; i < FryGroup.MAX_MEMBER_COUNT - 1; i++) {
            addMember(groupId, saveUser("filler" + i, "채움" + i));
        }
        Category study = saveCategory(joinerId, "공부");

        // when & then
        assertThatThrownBy(() -> groupService.join(
                new GroupJoinRequest(inviteCodeOf(groupId), List.of(study.getId())), joinerId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.GROUP_FULL.getMessage());

        assertThat(groupMemberRepository.existsByGroupIdAndUserId(groupId, joinerId)).isFalse();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("내 카테고리가 아니면 공개 대상으로 지정할 수 없다")
    void joinRejectsCategoryOwnedByOthers() {
        // given
        Category ownerCategory = saveCategory(ownerId, "운동");
        Long groupId = createGroup("바삭한 사람들", ownerId);

        // when & then
        assertThatThrownBy(() -> groupService.join(
                new GroupJoinRequest(inviteCodeOf(groupId), List.of(ownerCategory.getId())), joinerId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.CATEGORY_NOT_FOUND.getMessage());

        assertThat(groupMemberRepository.existsByGroupIdAndUserId(groupId, joinerId)).isFalse();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("초대 코드 미리보기는 그룹 정보와 참여 가능 여부를 함께 내려준다")
    void previewReturnsGroupInfo() {
        // given
        saveCategory(ownerId, "운동");
        Long groupId = createGroup("바삭한 사람들", ownerId);

        // when
        GroupInvitePreviewResponse response = groupService.previewByInviteCode(inviteCodeOf(groupId), joinerId);

        // then
        assertThat(response.getGroupId()).isEqualTo(groupId);
        assertThat(response.getName()).isEqualTo("바삭한 사람들");
        assertThat(response.getMemberCount()).isEqualTo(1);
        assertThat(response.isFull()).isFalse();
        assertThat(response.isAlreadyJoined()).isFalse();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("미리보기는 정원 초과와 이미 참여한 그룹을 플래그로 알려준다")
    void previewFlagsFullAndAlreadyJoined() {
        // given
        saveCategory(ownerId, "운동");
        Long groupId = createGroup("바삭한 사람들", ownerId);
        for (int i = 0; i < FryGroup.MAX_MEMBER_COUNT - 1; i++) {
            addMember(groupId, saveUser("filler" + i, "채움" + i));
        }

        // when
        GroupInvitePreviewResponse response = groupService.previewByInviteCode(inviteCodeOf(groupId), ownerId);

        // then
        assertThat(response.isFull()).isTrue();
        assertThat(response.isAlreadyJoined()).isTrue();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("존재하지 않는 초대 코드는 미리보기도 할 수 없다")
    void previewRejectsUnknownInviteCode() {
        assertThatThrownBy(() -> groupService.previewByInviteCode("ZZZZZZ", joinerId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVITE_CODE_NOT_FOUND.getMessage());
    }

    private Long createGroup(String name, Long userId) {
        return groupService.createGroup(new GroupCreateRequest(name), userId).getGroupId();
    }

    private String inviteCodeOf(Long groupId) {
        return fryGroupRepository.findById(groupId).orElseThrow().getInviteCode();
    }

    private void addMember(Long groupId, Long userId) {
        groupMemberRepository.saveAndFlush(GroupMember.builder().groupId(groupId).userId(userId).build());
    }

    private Long saveUser(String providerUserId, String nickname) {
        User user = User.createNewUser(AuthProvider.KAKAO, providerUserId, providerUserId + "@test.com");
        user.setNickname(nickname);
        return userJpaRepository.saveAndFlush(user).getId();
    }

    private Category saveCategory(Long userId, String name) {
        return categoryRepository.saveAndFlush(Category.builder()
                .name(name).color(CategoryColor.OR).userId(userId).displayOrder(1L).build());
    }
}
