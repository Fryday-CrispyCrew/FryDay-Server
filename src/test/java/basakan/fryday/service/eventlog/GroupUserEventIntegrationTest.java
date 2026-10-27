package basakan.fryday.service.eventlog;

import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.common.security.RefreshTokenRepository;
import basakan.fryday.controller.group.request.GroupCreateRequest;
import basakan.fryday.controller.group.request.GroupInteractionRequest;
import basakan.fryday.controller.group.request.GroupJoinRequest;
import basakan.fryday.controller.group.request.GroupNotificationSettingRequest;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.eventlog.EventType;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.auth.UserJpaRepository;
import basakan.fryday.repository.auth.client.SocialUserInfo;
import basakan.fryday.repository.group.FryGroupRepository;
import basakan.fryday.repository.group.GroupInteractionRepository;
import basakan.fryday.repository.group.GroupMemberRepository;
import basakan.fryday.repository.group.GroupPublicCategoryRepository;
import basakan.fryday.service.group.GroupCreator;
import basakan.fryday.service.group.GroupInteractionCooldown;
import basakan.fryday.service.group.GroupInteractionService;
import basakan.fryday.service.group.GroupService;
import basakan.fryday.service.group.InviteCodeGenerator;
import basakan.fryday.service.user.UserWriteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static basakan.fryday.domain.group.GroupInteractionType.KNOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:group_user_event_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        }
)
@Import({JpaConfig.class, UserWriteService.class, GroupService.class, GroupCreator.class, InviteCodeGenerator.class,
        GroupInteractionService.class})
@RecordApplicationEvents
@DisplayName("유저·그룹 유저 이벤트")
class GroupUserEventIntegrationTest {

    @MockitoBean private RefreshTokenRepository refreshTokenRepository;
    @MockitoBean private GroupInteractionCooldown cooldown;

    @Autowired private UserWriteService userWriteService;
    @Autowired private GroupService groupService;
    @Autowired private GroupInteractionService interactionService;
    @Autowired private ApplicationEvents events;
    @Autowired private FryGroupRepository fryGroupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private GroupPublicCategoryRepository groupPublicCategoryRepository;
    @Autowired private GroupInteractionRepository groupInteractionRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserJpaRepository userJpaRepository;

    private Long ownerId;
    private Long memberId;
    private Category memberCategory;

    @BeforeEach
    void setUp() {
        groupInteractionRepository.deleteAll();
        groupPublicCategoryRepository.deleteAll();
        groupMemberRepository.deleteAll();
        fryGroupRepository.deleteAll();
        categoryRepository.deleteAll();
        userJpaRepository.deleteAll();

        ownerId = saveUser("owner", "연우");
        memberId = saveUser("member", "수정");
        saveCategory(ownerId, "운동");
        memberCategory = saveCategory(memberId, "공부");

        given(cooldown.tryAcquire(anyLong(), anyLong(), anyLong(), any())).willReturn(true);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("가입하면 가입 이벤트를 발행한다")
    void createUserPublishesSignedUp() {
        // when
        User user = userWriteService.createUser(new SocialUserInfo(AuthProvider.KAKAO, "new-user", "new@test.com"));

        // then
        assertThat(eventsOf(EventType.USER_SIGNED_UP)).extracting(UserEvent::userId).containsExactly(user.getId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("탈퇴하면 탈퇴 이벤트와, 내가 만든 그룹의 해체 이벤트를 발행한다")
    void withdrawPublishesWithdrewAndDisbanded() {
        // given
        Long groupId = createGroup();

        // when
        userWriteService.withdraw(userJpaRepository.findById(ownerId).orElseThrow());

        // then
        assertThat(eventsOf(EventType.USER_WITHDREW)).extracting(UserEvent::userId).containsExactly(ownerId);
        assertThat(eventsOf(EventType.GROUP_DISBANDED)).extracting(UserEvent::userId, UserEvent::targetId)
                .containsExactly(tuple(ownerId, groupId));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("그룹을 만들고, 참여하고, 나가면 각각 이벤트를 발행한다")
    void groupLifecyclePublishesEvents() {
        // when
        Long groupId = createGroup();
        join(groupId, memberId);
        groupService.leaveGroup(groupId, memberId);

        // then
        assertThat(events.stream(UserEvent.class))
                .extracting(UserEvent::type, UserEvent::userId, UserEvent::targetId)
                .containsExactly(
                        tuple(EventType.GROUP_CREATED, ownerId, groupId),
                        tuple(EventType.GROUP_JOINED, memberId, groupId),
                        tuple(EventType.GROUP_LEFT, memberId, groupId));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("그룹을 해체하면 그룹장 이름으로 해체 이벤트를 발행한다")
    void deleteGroupPublishesDisbanded() {
        // given
        Long groupId = createGroup();
        join(groupId, memberId);

        // when
        groupService.deleteGroup(groupId, ownerId);

        // then
        assertThat(eventsOf(EventType.GROUP_DISBANDED)).extracting(UserEvent::userId, UserEvent::targetId)
                .containsExactly(tuple(ownerId, groupId));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("상호작용을 보내면 보낸 사람 기준으로 종류와 함께 이벤트를 발행한다")
    void interactPublishesInteractionSent() {
        // given
        Long groupId = createGroup();
        join(groupId, memberId);

        // when
        interactionService.interact(groupId, memberId, new GroupInteractionRequest(KNOCK), ownerId);

        // then
        List<UserEvent> sent = eventsOf(EventType.GROUP_INTERACTION_SENT);
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).userId()).isEqualTo(ownerId);
        assertThat(sent.get(0).targetId()).isEqualTo(groupId);
        assertThat(sent.get(0).properties()).isEqualTo(Map.of("type", "KNOCK"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("초대 코드로 그룹을 확인하면 참여 가능 여부와 함께 이벤트를 발행한다")
    void previewPublishesInviteCodePreviewed() {
        // given
        Long groupId = createGroup();
        String inviteCode = fryGroupRepository.findById(groupId).orElseThrow().getInviteCode();

        // when
        groupService.previewByInviteCode(inviteCode, memberId);
        groupService.previewByInviteCode(inviteCode, ownerId);

        // then
        assertThat(eventsOf(EventType.INVITE_CODE_PREVIEWED))
                .extracting(UserEvent::userId, UserEvent::targetId, UserEvent::properties)
                .containsExactly(
                        tuple(memberId, groupId, Map.of("full", false, "alreadyJoined", false)),
                        tuple(ownerId, groupId, Map.of("full", false, "alreadyJoined", true)));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("그룹 알림 설정은 값이 바뀐 경우에만 이벤트를 발행한다")
    void notificationTogglePublishesOnlyWhenChanged() {
        // given
        Long groupId = createGroup();
        join(groupId, memberId);

        // when
        groupService.updateNotificationSetting(groupId, memberId, new GroupNotificationSettingRequest(true));
        groupService.updateNotificationSetting(groupId, memberId, new GroupNotificationSettingRequest(false));
        groupService.updateNotificationSetting(groupId, memberId, new GroupNotificationSettingRequest(false));

        // then
        assertThat(eventsOf(EventType.GROUP_NOTIFICATION_TOGGLED))
                .extracting(UserEvent::userId, UserEvent::targetId, UserEvent::properties)
                .containsExactly(tuple(memberId, groupId, Map.of("enabled", false)));
    }

    private List<UserEvent> eventsOf(EventType type) {
        return events.stream(UserEvent.class).filter(event -> event.type() == type).toList();
    }

    private Long createGroup() {
        return groupService.createGroup(new GroupCreateRequest("바삭한 사람들"), ownerId).getGroupId();
    }

    private void join(Long groupId, Long userId) {
        String inviteCode = fryGroupRepository.findById(groupId).orElseThrow().getInviteCode();
        groupService.join(new GroupJoinRequest(inviteCode, List.of(memberCategory.getId())), userId);
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
