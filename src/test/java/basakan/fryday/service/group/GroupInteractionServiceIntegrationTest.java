package basakan.fryday.service.group;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.group.request.GroupCreateRequest;
import basakan.fryday.controller.group.request.GroupInteractionRequest;
import basakan.fryday.controller.group.request.GroupJoinRequest;
import basakan.fryday.controller.group.request.GroupNotificationSettingRequest;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.group.GroupInteraction;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.auth.UserJpaRepository;
import basakan.fryday.repository.group.FryGroupRepository;
import basakan.fryday.repository.group.GroupInteractionRepository;
import basakan.fryday.repository.group.GroupMemberRepository;
import basakan.fryday.repository.group.GroupPublicCategoryRepository;
import basakan.fryday.repository.todo.TodoRepository;
import basakan.fryday.service.group.event.GroupInteractionEvent;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static basakan.fryday.domain.group.GroupInteractionType.KNOCK;
import static basakan.fryday.domain.group.GroupInteractionType.ORDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:group_interaction_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        }
)
@Import({JpaConfig.class, GroupService.class, GroupCreator.class, InviteCodeGenerator.class,
        GroupInteractionService.class})
@RecordApplicationEvents
@DisplayName("그룹 상호작용")
class GroupInteractionServiceIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @MockitoBean private GroupInteractionCooldown cooldown;

    @Autowired private GroupInteractionService interactionService;
    @Autowired private GroupService groupService;
    @Autowired private ApplicationEvents events;
    @Autowired private FryGroupRepository fryGroupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private GroupPublicCategoryRepository groupPublicCategoryRepository;
    @Autowired private GroupInteractionRepository groupInteractionRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TodoRepository todoRepository;
    @Autowired private UserJpaRepository userJpaRepository;

    private Long senderId;
    private Long targetId;
    private Long strangerId;
    private Long groupId;
    private Category targetCategory;

    @BeforeEach
    void setUp() {
        groupInteractionRepository.deleteAll();
        todoRepository.deleteAll();
        groupPublicCategoryRepository.deleteAll();
        groupMemberRepository.deleteAll();
        fryGroupRepository.deleteAll();
        categoryRepository.deleteAll();
        userJpaRepository.deleteAll();

        senderId = saveUser("sender", "연우");
        targetId = saveUser("target", "수정");
        strangerId = saveUser("stranger", "낯선이");

        saveCategory(senderId, "운동");
        groupId = groupService.createGroup(new GroupCreateRequest("바삭한 사람들"), senderId).getGroupId();
        targetCategory = saveCategory(targetId, "공부");
        String inviteCode = fryGroupRepository.findById(groupId).orElseThrow().getInviteCode();
        groupService.join(new GroupJoinRequest(inviteCode, List.of(targetCategory.getId())), targetId);

        given(cooldown.tryAcquire(anyLong(), anyLong(), anyLong(), any())).willReturn(true);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("상태에 맞는 버튼을 보내면 기록을 남기고 받는 사람에게 알림 이벤트를 발행한다")
    void interactionIsRecordedAndNotified() {
        // given — 투두는 있지만 완료 0개인 영업 준비 상태
        saveTodo(targetCategory);

        // when
        interactionService.interact(groupId, targetId, new GroupInteractionRequest(ORDER), senderId);

        // then
        assertThat(groupInteractionRepository.findAll())
                .extracting(GroupInteraction::getSenderId, GroupInteraction::getTargetId, GroupInteraction::getType)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(senderId, targetId, ORDER));
        assertThat(events.stream(GroupInteractionEvent.class))
                .containsExactly(new GroupInteractionEvent(groupId, "바삭한 사람들", "연우", targetId, ORDER));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("오늘 공개 투두가 없는 그룹원에게는 똑똑똑을 보낼 수 있다")
    void knockToMemberBeforeOpen() {
        // when
        interactionService.interact(groupId, targetId, new GroupInteractionRequest(KNOCK), senderId);

        // then
        assertThat(groupInteractionRepository.count()).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("받는 사람의 현재 상태와 버튼이 맞지 않으면 보낼 수 없다")
    void rejectsMismatchedButton() {
        // given — 영업 준비 상태에 똑똑똑
        saveTodo(targetCategory);

        // when & then
        assertThatThrownBy(() -> interactionService.interact(
                groupId, targetId, new GroupInteractionRequest(KNOCK), senderId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INTERACTION_STATUS_MISMATCH.getMessage());
        assertThat(groupInteractionRepository.count()).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("쿨다운 중이면 보낼 수 없고 기록도 알림도 남기지 않는다")
    void rejectsDuringCooldown() {
        // given
        given(cooldown.tryAcquire(groupId, senderId, targetId, KNOCK)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> interactionService.interact(
                groupId, targetId, new GroupInteractionRequest(KNOCK), senderId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INTERACTION_COOLDOWN.getMessage());
        assertThat(groupInteractionRepository.count()).isZero();
        assertThat(events.stream(GroupInteractionEvent.class)).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("받는 사람이 그룹 알림을 껐으면 기록만 남기고 알림은 보내지 않는다")
    void recordsWithoutNotificationWhenTargetTurnedOff() {
        // given
        groupService.updateNotificationSetting(groupId, targetId, new GroupNotificationSettingRequest(false));

        // when
        interactionService.interact(groupId, targetId, new GroupInteractionRequest(KNOCK), senderId);

        // then
        assertThat(groupInteractionRepository.count()).isEqualTo(1);
        assertThat(events.stream(GroupInteractionEvent.class)).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("자신에게는 보낼 수 없다")
    void rejectsSelf() {
        assertThatThrownBy(() -> interactionService.interact(
                groupId, senderId, new GroupInteractionRequest(KNOCK), senderId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INTERACTION_SELF_NOT_ALLOWED.getMessage());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("보내는 사람이나 받는 사람이 그룹원이 아니면 보낼 수 없다")
    void rejectsNonMembers() {
        assertThatThrownBy(() -> interactionService.interact(
                groupId, targetId, new GroupInteractionRequest(KNOCK), strangerId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.GROUP_NOT_FOUND.getMessage());
        assertThatThrownBy(() -> interactionService.interact(
                groupId, strangerId, new GroupInteractionRequest(KNOCK), senderId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.GROUP_NOT_FOUND.getMessage());
    }

    private void saveTodo(Category category) {
        todoRepository.saveAndFlush(Todo.builder()
                .description("투두").category(category).date(TODAY).displayOrder(1L).build());
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
