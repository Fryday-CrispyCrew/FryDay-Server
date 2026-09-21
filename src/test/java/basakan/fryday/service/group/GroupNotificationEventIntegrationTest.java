package basakan.fryday.service.group;

import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.controller.group.request.GroupCreateRequest;
import basakan.fryday.controller.group.request.GroupJoinRequest;
import basakan.fryday.controller.group.request.GroupNotificationSettingRequest;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.auth.UserJpaRepository;
import basakan.fryday.repository.group.FryGroupRepository;
import basakan.fryday.repository.group.GroupMemberRepository;
import basakan.fryday.repository.group.GroupPublicCategoryRepository;
import basakan.fryday.service.group.event.GroupDisbandedEvent;
import basakan.fryday.service.group.event.GroupJoinedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:group_notification_event_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
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
@RecordApplicationEvents
@DisplayName("그룹 알림 이벤트")
class GroupNotificationEventIntegrationTest {

    @Autowired private GroupService groupService;
    @Autowired private ApplicationEvents events;
    @Autowired private FryGroupRepository fryGroupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private GroupPublicCategoryRepository groupPublicCategoryRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserJpaRepository userJpaRepository;

    private Long ownerId;
    private Long memberId;
    private Long joinerId;

    @BeforeEach
    void setUp() {
        groupPublicCategoryRepository.deleteAll();
        groupMemberRepository.deleteAll();
        fryGroupRepository.deleteAll();
        categoryRepository.deleteAll();
        userJpaRepository.deleteAll();

        ownerId = saveUser("owner", "연우");
        memberId = saveUser("member", "수정");
        joinerId = saveUser("joiner", "지민");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("참여하면 참여자 본인을 뺀 그룹원에게 참여 알림을 보낸다")
    void joinNotifiesOtherMembers() {
        // given
        Long groupId = createGroup("바삭한 사람들");
        join(groupId, memberId);

        // when
        join(groupId, joinerId);

        // then
        GroupJoinedEvent event = lastJoinedEvent();
        assertThat(event.groupId()).isEqualTo(groupId);
        assertThat(event.groupName()).isEqualTo("바삭한 사람들");
        assertThat(event.joinerNickname()).isEqualTo("지민");
        assertThat(event.recipientIds()).containsExactlyInAnyOrder(ownerId, memberId);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("그룹 알림을 끈 그룹원은 수신자에서 빠진다")
    void excludesMembersWhoTurnedOffNotification() {
        // given
        Long groupId = createGroup("바삭한 사람들");
        join(groupId, memberId);
        groupService.updateNotificationSetting(groupId, memberId, new GroupNotificationSettingRequest(false));

        // when
        join(groupId, joinerId);

        // then
        assertThat(lastJoinedEvent().recipientIds()).containsExactly(ownerId);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("탈퇴 처리된 계정은 수신자에서 빠진다")
    void excludesWithdrawnAccounts() {
        // given
        Long groupId = createGroup("바삭한 사람들");
        join(groupId, memberId);
        User member = userJpaRepository.findById(memberId).orElseThrow();
        member.withdraw();
        userJpaRepository.saveAndFlush(member);

        // when
        join(groupId, joinerId);

        // then
        assertThat(lastJoinedEvent().recipientIds()).containsExactly(ownerId);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("닉네임이 없는 사용자가 참여하면 닉네임 없이 이벤트를 보낸다")
    void joinWithoutNickname() {
        // given
        Long groupId = createGroup("바삭한 사람들");
        Long noNicknameId = saveUser("onboarding", null);

        // when
        join(groupId, noNicknameId);

        // then
        assertThat(lastJoinedEvent().joinerNickname()).isNull();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("해체하면 그룹장을 뺀 그룹원에게 해체 알림을 보낸다")
    void disbandNotifiesMembersExceptOwner() {
        // given
        Long groupId = createGroup("바삭한 사람들");
        join(groupId, memberId);
        join(groupId, joinerId);

        // when
        groupService.deleteGroup(groupId, ownerId);

        // then
        List<GroupDisbandedEvent> disbanded = events.stream(GroupDisbandedEvent.class).toList();
        assertThat(disbanded).hasSize(1);
        assertThat(disbanded.get(0).groupName()).isEqualTo("바삭한 사람들");
        assertThat(disbanded.get(0).recipientIds()).containsExactlyInAnyOrder(memberId, joinerId);
    }

    private GroupJoinedEvent lastJoinedEvent() {
        List<GroupJoinedEvent> joined = events.stream(GroupJoinedEvent.class).toList();
        return joined.get(joined.size() - 1);
    }

    private Long createGroup(String name) {
        saveCategory(ownerId, "운동");
        return groupService.createGroup(new GroupCreateRequest(name), ownerId).getGroupId();
    }

    private void join(Long groupId, Long userId) {
        Category category = saveCategory(userId, "공부");
        String inviteCode = fryGroupRepository.findById(groupId).orElseThrow().getInviteCode();
        groupService.join(new GroupJoinRequest(inviteCode, List.of(category.getId())), userId);
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
