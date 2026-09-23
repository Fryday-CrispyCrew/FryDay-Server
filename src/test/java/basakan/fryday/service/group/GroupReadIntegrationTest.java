package basakan.fryday.service.group;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.group.request.GroupCreateRequest;
import basakan.fryday.controller.group.response.GroupDetailResponse;
import basakan.fryday.controller.group.response.GroupListResponse;
import basakan.fryday.controller.group.response.GroupMemberResponse;
import basakan.fryday.controller.group.response.GroupSummaryResponse;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.group.GroupInteractionType;
import basakan.fryday.domain.group.GroupMember;
import basakan.fryday.domain.group.GroupMemberStatus;
import basakan.fryday.domain.group.GroupRole;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.auth.UserJpaRepository;
import basakan.fryday.repository.group.FryGroupRepository;
import basakan.fryday.repository.group.GroupMemberRepository;
import basakan.fryday.repository.group.GroupPublicCategoryRepository;
import basakan.fryday.repository.todo.TodoRepository;
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

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:group_read_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
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
@DisplayName("그룹 통합 조회")
class GroupReadIntegrationTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    @Autowired private GroupService groupService;
    @Autowired private FryGroupRepository fryGroupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private GroupPublicCategoryRepository groupPublicCategoryRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TodoRepository todoRepository;
    @Autowired private UserJpaRepository userJpaRepository;

    private Long ownerId;
    private Long memberId;
    private Long strangerId;

    @BeforeEach
    void setUp() {
        todoRepository.deleteAll();
        groupPublicCategoryRepository.deleteAll();
        groupMemberRepository.deleteAll();
        fryGroupRepository.deleteAll();
        categoryRepository.deleteAll();
        userJpaRepository.deleteAll();

        ownerId = saveUser("owner", "연우");
        memberId = saveUser("member", "수정");
        strangerId = saveUser("stranger", "낯선이");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("그룹 정보와 그룹원별 오늘 투두 진행 상태를 한 번에 조회한다")
    void getGroupReturnsMembersWithTodayProgress() {
        // given
        Category ownerCategory = saveCategory(ownerId, "운동");
        Long groupId = createGroup("바삭한 사람들", ownerId);
        join(groupId, memberId);

        Category memberCategory = saveCategory(memberId, "공부");
        publish(groupId, memberId, memberCategory);

        saveTodo(ownerCategory, Todo.Status.COMPLETED);
        saveTodo(ownerCategory, Todo.Status.IN_PROGRESS);
        saveTodo(ownerCategory, Todo.Status.IN_PROGRESS);
        saveTodo(memberCategory, Todo.Status.COMPLETED);

        // when
        GroupDetailResponse response = groupService.getGroup(groupId, ownerId);

        // then
        assertThat(response.getName()).isEqualTo("바삭한 사람들");
        assertThat(response.getInviteCode()).hasSize(6);
        assertThat(response.getMemberCount()).isEqualTo(2);
        assertThat(response.getMaxMemberCount()).isEqualTo(10);
        assertThat(response.getMyRole()).isEqualTo(GroupRole.OWNER);
        assertThat(response.getMyPublicCategoryCount()).isEqualTo(1);
        assertThat(response.getDate()).isEqualTo(LocalDate.now(KOREA_ZONE));

        assertThat(response.getMembers())
                .extracting(GroupMemberResponse::getNickname,
                        GroupMemberResponse::getTotalCount,
                        GroupMemberResponse::getCompletedCount,
                        GroupMemberResponse::getStatus,
                        GroupMemberResponse::getAvailableInteraction)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("연우", 3, 1,
                                GroupMemberStatus.FRYING, GroupInteractionType.DELICIOUS),
                        org.assertj.core.groups.Tuple.tuple("수정", 1, 1,
                                GroupMemberStatus.CLOSED, GroupInteractionType.APPLAUSE));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("오늘 투두가 없는 그룹원도 0/0으로 목록에 포함된다")
    void memberWithoutTodosIsIncludedWithZeroCounts() {
        // given
        saveCategory(ownerId, "운동");
        Long groupId = createGroup("바삭한 사람들", ownerId);
        join(groupId, memberId);

        // when
        GroupDetailResponse response = groupService.getGroup(groupId, ownerId);

        // then
        assertThat(response.getMembers()).hasSize(2);
        assertThat(response.getMembers())
                .allSatisfy(member -> {
                    assertThat(member.getTotalCount()).isZero();
                    assertThat(member.getCompletedCount()).isZero();
                });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("나중에 참여한 그룹장이라도 나 바로 다음에 온다")
    void ownerIsAlwaysRightAfterViewer() {
        // given — 그룹장보다 memberId 가 먼저 참여한 상황을 만든다
        saveCategory(ownerId, "운동");
        Long groupId = createGroup("바삭한 사람들", ownerId);
        groupMemberRepository.deleteAll();
        join(groupId, memberId);
        join(groupId, strangerId);
        join(groupId, ownerId);

        // when
        GroupDetailResponse response = groupService.getGroup(groupId, strangerId);

        // then
        assertThat(response.getMembers())
                .extracting(GroupMemberResponse::getNickname)
                .containsExactly("낯선이", "연우", "수정");
        assertThat(response.getMembers().get(0).getRole()).isEqualTo(GroupRole.MEMBER);
        assertThat(response.getMembers().get(1).getRole()).isEqualTo(GroupRole.OWNER);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("그룹원이 조회하면 나, 그룹장, 나머지 그룹원 참여 순서로 온다")
    void viewerIsFirstThenOwner() {
        // given — 참여 순서는 연우(그룹장), 수정, 낯선이
        saveCategory(ownerId, "운동");
        Long groupId = createGroup("바삭한 사람들", ownerId);
        join(groupId, memberId);
        join(groupId, strangerId);

        // when — 마지막에 참여한 낯선이가 조회한다
        GroupDetailResponse response = groupService.getGroup(groupId, strangerId);

        // then
        assertThat(response.getMembers())
                .extracting(GroupMemberResponse::getNickname)
                .containsExactly("낯선이", "연우", "수정");
        assertThat(response.getMembers().get(1).getRole()).isEqualTo(GroupRole.OWNER);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("나와 그룹장을 뺀 나머지 그룹원은 참여 순서를 그대로 유지한다")
    void remainingMembersKeepJoinOrder() {
        // given — 참여 순서는 연우(그룹장), 수정, 낯선이, 넷째, 다섯째
        saveCategory(ownerId, "운동");
        Long fourthId = saveUser("fourth", "넷째");
        Long fifthId = saveUser("fifth", "다섯째");
        Long groupId = createGroup("바삭한 사람들", ownerId);
        join(groupId, memberId);
        join(groupId, strangerId);
        join(groupId, fourthId);
        join(groupId, fifthId);

        // when — 마지막에 참여한 다섯째가 조회한다
        GroupDetailResponse response = groupService.getGroup(groupId, fifthId);

        // then — 나머지 3명(수정, 낯선이, 넷째)의 참여 순서가 뒤집히지 않는다
        assertThat(response.getMembers())
                .extracting(GroupMemberResponse::getNickname)
                .containsExactly("다섯째", "연우", "수정", "낯선이", "넷째");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("내가 그룹장이면 나와 그룹장 자리가 겹쳐도 한 번만 나온다")
    void ownerViewingOwnGroupAppearsOnce() {
        // given — 그룹장이 가장 늦게 참여해 두 순위 조건에 모두 해당하는 상황
        saveCategory(ownerId, "운동");
        Long groupId = createGroup("바삭한 사람들", ownerId);
        groupMemberRepository.deleteAll();
        join(groupId, memberId);
        join(groupId, strangerId);
        join(groupId, ownerId);

        // when
        GroupDetailResponse response = groupService.getGroup(groupId, ownerId);

        // then
        assertThat(response.getMembers())
                .extracting(GroupMemberResponse::getNickname)
                .containsExactly("연우", "수정", "낯선이");
        assertThat(response.getMemberCount()).isEqualTo(3);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("그룹원이 조회하면 내 권한은 MEMBER 로 내려온다")
    void memberSeesMemberRole() {
        // given
        saveCategory(ownerId, "운동");
        Long groupId = createGroup("바삭한 사람들", ownerId);
        join(groupId, memberId);

        // when
        GroupDetailResponse response = groupService.getGroup(groupId, memberId);

        // then
        assertThat(response.getMyRole()).isEqualTo(GroupRole.MEMBER);
        assertThat(response.getMyPublicCategoryCount()).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("그룹원이 아니면 그룹을 조회할 수 없다")
    void strangerCannotReadGroup() {
        // given
        saveCategory(ownerId, "운동");
        Long groupId = createGroup("바삭한 사람들", ownerId);

        // when & then
        assertThatThrownBy(() -> groupService.getGroup(groupId, strangerId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.GROUP_NOT_FOUND.getMessage());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("내 그룹 목록은 최근 가입한 그룹이 맨 앞에 온다")
    void myGroupsAreSortedByRecentJoin() {
        // given
        saveCategory(ownerId, "운동");
        Long first = createGroup("첫번째", ownerId);
        Long second = createGroup("두번째", ownerId);
        Long third = createGroup("세번째", ownerId);

        // when
        GroupListResponse response = groupService.getMyGroups(ownerId);

        // then
        assertThat(response.getGroups())
                .extracting(GroupSummaryResponse::getGroupId)
                .containsExactly(third, second, first);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("내 그룹 목록에는 인원수와 내 권한이 함께 내려온다")
    void myGroupsCarryMemberCountAndRole() {
        // given
        saveCategory(ownerId, "운동");
        Long ownedGroup = createGroup("내가 만든 그룹", ownerId);
        join(ownedGroup, memberId);

        saveCategory(strangerId, "독서");
        Long joinedGroup = createGroup("남이 만든 그룹", strangerId);
        join(joinedGroup, ownerId);

        // when
        GroupListResponse response = groupService.getMyGroups(ownerId);

        // then
        assertThat(response.getGroups())
                .extracting(GroupSummaryResponse::getName,
                        GroupSummaryResponse::getMemberCount,
                        GroupSummaryResponse::getMyRole)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("남이 만든 그룹", 2, GroupRole.MEMBER),
                        org.assertj.core.groups.Tuple.tuple("내가 만든 그룹", 2, GroupRole.OWNER));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("참여한 그룹이 없으면 빈 목록을 내려준다")
    void myGroupsIsEmptyWhenNotJoined() {
        // when
        GroupListResponse response = groupService.getMyGroups(strangerId);

        // then
        assertThat(response.getGroups()).isEmpty();
    }

    private Long createGroup(String name, Long userId) {
        return groupService.createGroup(new GroupCreateRequest(name), userId).getGroupId();
    }

    private void join(Long groupId, Long userId) {
        groupMemberRepository.saveAndFlush(GroupMember.builder().groupId(groupId).userId(userId).build());
    }

    private void publish(Long groupId, Long userId, Category category) {
        groupPublicCategoryRepository.saveAndFlush(basakan.fryday.domain.group.GroupPublicCategory.builder()
                .groupId(groupId).userId(userId).categoryId(category.getId()).build());
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

    private void saveTodo(Category category, Todo.Status status) {
        Todo todo = Todo.builder()
                .description("투두").category(category).date(LocalDate.now(KOREA_ZONE)).displayOrder(1L).build();
        if (status == Todo.Status.COMPLETED) {
            todo.toggleCompletion();
        }
        todoRepository.saveAndFlush(todo);
    }
}
