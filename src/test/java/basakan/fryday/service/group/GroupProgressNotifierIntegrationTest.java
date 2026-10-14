package basakan.fryday.service.group;

import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.controller.group.request.GroupCreateRequest;
import basakan.fryday.controller.group.request.GroupJoinRequest;
import basakan.fryday.controller.group.request.GroupNotificationSettingRequest;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.group.GroupPushHistory;
import basakan.fryday.domain.group.GroupPushType;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.auth.UserJpaRepository;
import basakan.fryday.repository.group.FryGroupRepository;
import basakan.fryday.repository.group.GroupMemberRepository;
import basakan.fryday.repository.group.GroupPublicCategoryRepository;
import basakan.fryday.repository.group.GroupPushHistoryRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:group_progress_notifier_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
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
        GroupProgressNotifier.class, GroupPushHistoryRecorder.class})
@DisplayName("튀기기 시작과 영업종료 알림 판단")
class GroupProgressNotifierIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @MockitoBean private GroupPushSender groupPushSender;

    @Autowired private GroupProgressNotifier notifier;
    @Autowired private GroupService groupService;
    @Autowired private FryGroupRepository fryGroupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private GroupPublicCategoryRepository groupPublicCategoryRepository;
    @Autowired private GroupPushHistoryRepository groupPushHistoryRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TodoRepository todoRepository;
    @Autowired private UserJpaRepository userJpaRepository;

    private Long ownerId;
    private Long memberId;
    private Long actorId;

    @BeforeEach
    void setUp() {
        groupPushHistoryRepository.deleteAll();
        todoRepository.deleteAll();
        groupPublicCategoryRepository.deleteAll();
        groupMemberRepository.deleteAll();
        fryGroupRepository.deleteAll();
        categoryRepository.deleteAll();
        userJpaRepository.deleteAll();

        ownerId = saveUser("owner", "연우");
        memberId = saveUser("member", "수정");
        actorId = saveUser("actor", "지민");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("공개 투두 여러 개 중 처음 완료하면 튀기기 시작 알림을 본인을 뺀 그룹원에게 보낸다")
    void firstCompletionSendsFryingStarted() {
        // given
        Category study = saveCategory(actorId, "공부");
        Long groupId = groupWithActor("바삭한 사람들", study);
        Todo first = saveTodo(study);
        saveTodo(study);
        saveTodo(study);

        // when
        complete(first);
        notifier.notifyProgress(actorId);

        // then
        then(groupPushSender).should().send(
                argThat(ids -> ids.size() == 2 && ids.containsAll(List.of(ownerId, memberId))),
                eq("바삭한 사람들"),
                eq("지민님이 튀김을 튀기기 시작했어요!"),
                eq(Map.of("type", "GROUP_FRYING_STARTED", "groupId", String.valueOf(groupId))));
        assertThat(sentTypes()).containsExactly(GroupPushType.GROUP_FRYING_STARTED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("공개 투두를 전부 완료하면 영업종료 알림을 보낸다")
    void allCompletedSendsFryingFinished() {
        // given
        Category study = saveCategory(actorId, "공부");
        groupWithActor("바삭한 사람들", study);
        Todo first = saveTodo(study);
        Todo second = saveTodo(study);
        complete(first);
        notifier.notifyProgress(actorId);

        // when
        complete(second);
        notifier.notifyProgress(actorId);

        // then
        then(groupPushSender).should().send(anyList(), eq("바삭한 사람들"),
                eq("지민님이 튀김을 다 튀겼습니다!"), anyMap());
        assertThat(sentTypes()).containsExactlyInAnyOrder(
                GroupPushType.GROUP_FRYING_STARTED, GroupPushType.GROUP_FRYING_FINISHED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("공개 투두가 1개뿐이면 튀기기 시작 없이 영업종료만 보낸다")
    void singleTodoSendsOnlyFinished() {
        // given
        Category study = saveCategory(actorId, "공부");
        groupWithActor("바삭한 사람들", study);
        Todo only = saveTodo(study);

        // when
        complete(only);
        notifier.notifyProgress(actorId);

        // then
        then(groupPushSender).should(times(1)).send(anyList(), anyString(), anyString(), anyMap());
        assertThat(sentTypes()).containsExactly(GroupPushType.GROUP_FRYING_FINISHED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("완료를 취소했다가 다시 완료해도 같은 날에는 다시 보내지 않는다")
    void recompletionDoesNotResend() {
        // given
        Category study = saveCategory(actorId, "공부");
        groupWithActor("바삭한 사람들", study);
        Todo first = saveTodo(study);
        saveTodo(study);
        complete(first);
        notifier.notifyProgress(actorId);

        // when
        complete(first);
        complete(first);
        notifier.notifyProgress(actorId);

        // then
        then(groupPushSender).should(times(1)).send(anyList(), anyString(), anyString(), anyMap());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("남은 미완료 투두를 지워 전부 완료가 되어도 영업종료를 보낸다")
    void deletingRemainingTodoSendsFinished() {
        // given
        Category study = saveCategory(actorId, "공부");
        groupWithActor("바삭한 사람들", study);
        Todo first = saveTodo(study);
        Todo remaining = saveTodo(study);
        complete(first);
        notifier.notifyProgress(actorId);

        // when
        remaining.delete();
        todoRepository.saveAndFlush(remaining);
        notifier.notifyProgress(actorId);

        // then
        assertThat(sentTypes()).contains(GroupPushType.GROUP_FRYING_FINISHED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("영업종료가 나간 날에는 투두를 추가해 완료해도 튀기기 시작을 보내지 않는다")
    void noFryingStartedAfterFinished() {
        // given
        Category study = saveCategory(actorId, "공부");
        groupWithActor("바삭한 사람들", study);
        complete(saveTodo(study));
        notifier.notifyProgress(actorId);

        // when
        Todo added = saveTodo(study);
        saveTodo(study);
        complete(added);
        notifier.notifyProgress(actorId);

        // then
        assertThat(sentTypes()).containsExactly(GroupPushType.GROUP_FRYING_FINISHED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("그룹마다 공개 카테고리가 다르면 그룹별로 따로 판단한다")
    void judgesEachGroupByItsPublicCategories() {
        // given
        Category study = saveCategory(actorId, "공부");
        Category exercise = saveCategory(actorId, "운동");
        groupWithActor("공부 모임", study);
        groupWithActor("운동 모임", exercise);
        Todo studyTodo = saveTodo(study);
        saveTodo(study);
        saveTodo(exercise);

        // when
        complete(studyTodo);
        notifier.notifyProgress(actorId);

        // then
        then(groupPushSender).should(times(1)).send(anyList(), anyString(), anyString(), anyMap());
        then(groupPushSender).should().send(anyList(), eq("공부 모임"), anyString(), anyMap());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("그룹 알림을 끈 그룹원은 받지 않는다")
    void excludesMembersWhoTurnedOffNotification() {
        // given
        Category study = saveCategory(actorId, "공부");
        Long groupId = groupWithActor("바삭한 사람들", study);
        groupService.updateNotificationSetting(groupId, memberId, new GroupNotificationSettingRequest(false));
        Todo first = saveTodo(study);
        saveTodo(study);

        // when
        complete(first);
        notifier.notifyProgress(actorId);

        // then
        then(groupPushSender).should().send(eq(List.of(ownerId)), anyString(), anyString(), anyMap());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("완료한 공개 투두가 없으면 아무것도 보내지 않는다")
    void nothingCompletedSendsNothing() {
        // given
        Category study = saveCategory(actorId, "공부");
        groupWithActor("바삭한 사람들", study);
        saveTodo(study);

        // when
        notifier.notifyProgress(actorId);

        // then
        then(groupPushSender).should(never()).send(any(), any(), any(), any());
        assertThat(sentTypes()).isEmpty();
    }

    private Long groupWithActor(String name, Category actorCategory) {
        saveCategory(ownerId, "운동");
        Long groupId = groupService.createGroup(new GroupCreateRequest(name), ownerId).getGroupId();
        join(groupId, memberId, saveCategory(memberId, "독서"));
        join(groupId, actorId, actorCategory);
        clearInvocations(groupPushSender);
        return groupId;
    }

    private void join(Long groupId, Long userId, Category category) {
        String inviteCode = fryGroupRepository.findById(groupId).orElseThrow().getInviteCode();
        groupService.join(new GroupJoinRequest(inviteCode, List.of(category.getId())), userId);
    }

    private List<GroupPushType> sentTypes() {
        return groupPushHistoryRepository.findAllByUserIdAndPushDate(actorId, TODAY).stream()
                .map(GroupPushHistory::getType)
                .toList();
    }

    private void complete(Todo todo) {
        todo.toggleCompletion();
        todoRepository.saveAndFlush(todo);
    }

    private Todo saveTodo(Category category) {
        return todoRepository.saveAndFlush(Todo.builder()
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
