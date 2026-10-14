package basakan.fryday.service.group;

import basakan.fryday.common.config.AsyncConfig;
import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.common.service.push.PushService;
import basakan.fryday.controller.group.request.GroupCreateRequest;
import basakan.fryday.controller.group.request.GroupJoinRequest;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
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
import basakan.fryday.service.todo.RecurrenceOccurrenceMaterializeService;
import basakan.fryday.service.todo.TodoService;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:group_notification_delivery_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        }
)
@Import({JpaConfig.class, AsyncConfig.class, GroupService.class, GroupCreator.class, InviteCodeGenerator.class,
        GroupNotificationListener.class, GroupPushSender.class, GroupProgressNotifier.class,
        GroupPushHistoryRecorder.class, TodoService.class})
@DisplayName("그룹 알림 발송 배선")
class GroupNotificationDeliveryTest {

    @MockitoBean private PushService pushService;
    @MockitoBean private RecurrenceOccurrenceMaterializeService materializeService;

    @Autowired private GroupService groupService;
    @Autowired private TodoService todoService;
    @Autowired private TodoRepository todoRepository;
    @Autowired private GroupPushHistoryRepository groupPushHistoryRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private FryGroupRepository fryGroupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private GroupPublicCategoryRepository groupPublicCategoryRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserJpaRepository userJpaRepository;

    private Long ownerId;
    private Long joinerId;

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
        joinerId = saveUser("joiner", "지민");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("참여가 커밋되면 다른 스레드에서 그룹원에게 푸시를 보낸다")
    void sendsPushAfterCommit() {
        // given
        Long groupId = createGroup("바삭한 사람들");

        // when
        join(groupId, joinerId);

        // then
        verify(pushService, timeout(2000)).sendToUser(
                argThat(user -> user.getId().equals(ownerId)),
                eq("바삭한 사람들"),
                eq("지민님이 그룹에 참여했습니다."),
                eq(Map.of("type", "GROUP_JOINED", "groupId", String.valueOf(groupId))));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("참여가 롤백되면 푸시를 보내지 않는다")
    void doesNotSendPushWhenRolledBack() {
        // given
        Long groupId = createGroup("바삭한 사람들");
        clearInvocations(pushService);

        // when
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            join(groupId, joinerId);
            status.setRollbackOnly();
        });

        // then
        verify(pushService, after(500).never()).sendToUser(any(), anyString(), anyString(), anyMap());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("투두 완료가 커밋되면 다른 스레드에서 튀기기 시작 알림을 보낸다")
    void sendsFryingStartedAfterTodoCompletionCommit() {
        // given
        Long groupId = createGroup("바삭한 사람들");
        Category study = join(groupId, joinerId);
        Todo first = saveTodo(study);
        saveTodo(study);
        clearInvocations(pushService);

        // when
        todoService.toggleTodoCompletion(first.getId(), joinerId);

        // then
        verify(pushService, timeout(2000)).sendToUser(
                argThat(user -> user.getId().equals(ownerId)),
                eq("바삭한 사람들"),
                eq("지민님이 튀김을 튀기기 시작했어요!"),
                eq(Map.of("type", "GROUP_FRYING_STARTED", "groupId", String.valueOf(groupId))));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("투두 완료가 롤백되면 튀기기 시작 알림을 보내지 않는다")
    void doesNotSendFryingStartedWhenRolledBack() {
        // given
        Long groupId = createGroup("바삭한 사람들");
        Category study = join(groupId, joinerId);
        Todo first = saveTodo(study);
        saveTodo(study);
        // 참여 알림은 비동기로 발송되므로, 도착한 뒤에 비워야 아래 never() 검증에 섞이지 않는다.
        verify(pushService, timeout(2000)).sendToUser(any(), anyString(), anyString(),
                argThat(data -> "GROUP_JOINED".equals(data.get("type"))));
        clearInvocations(pushService);

        // when
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            todoService.toggleTodoCompletion(first.getId(), joinerId);
            status.setRollbackOnly();
        });

        // then
        verify(pushService, after(500).never()).sendToUser(any(), anyString(), anyString(), anyMap());
    }

    private Todo saveTodo(Category category) {
        return todoRepository.saveAndFlush(Todo.builder()
                .description("투두").category(category).date(LocalDate.now(ZoneId.of("Asia/Seoul")))
                .displayOrder(1L).build());
    }

    private Long createGroup(String name) {
        saveCategory(ownerId, "운동");
        return groupService.createGroup(new GroupCreateRequest(name), ownerId).getGroupId();
    }

    private Category join(Long groupId, Long userId) {
        Category category = saveCategory(userId, "공부");
        String inviteCode = fryGroupRepository.findById(groupId).orElseThrow().getInviteCode();
        groupService.join(new GroupJoinRequest(inviteCode, List.of(category.getId())), userId);
        return category;
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
