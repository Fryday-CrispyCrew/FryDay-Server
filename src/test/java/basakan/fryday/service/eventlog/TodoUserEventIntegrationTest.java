package basakan.fryday.service.eventlog;

import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.controller.todo.request.RecurrenceCreateRequest;
import basakan.fryday.controller.todo.request.TodoSaveRequest;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.eventlog.EventType;
import basakan.fryday.domain.todo.RecurrenceScope;
import basakan.fryday.domain.todo.RecurrenceType;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.auth.UserJpaRepository;
import basakan.fryday.repository.todo.TodoRepository;
import basakan.fryday.service.todo.RecurrenceInstanceService;
import basakan.fryday.service.todo.RecurrenceOccurrenceCalculator;
import basakan.fryday.service.todo.RecurrenceOccurrenceMaterializeService;
import basakan.fryday.service.todo.RecurrenceOccurrenceWriter;
import basakan.fryday.service.todo.RecurrenceService;
import basakan.fryday.service.todo.TodoAlarmSynchronizer;
import basakan.fryday.service.todo.TodoService;
import basakan.fryday.service.user.UserReadService;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:todo_user_event_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        }
)
@Import({
        JpaConfig.class,
        TodoService.class,
        RecurrenceService.class,
        RecurrenceInstanceService.class,
        RecurrenceOccurrenceWriter.class,
        RecurrenceOccurrenceMaterializeService.class,
        RecurrenceOccurrenceCalculator.class,
        TodoAlarmSynchronizer.class,
        UserReadService.class
})
@RecordApplicationEvents
@DisplayName("투두·반복 유저 이벤트")
class TodoUserEventIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired private TodoService todoService;
    @Autowired private RecurrenceService recurrenceService;
    @Autowired private RecurrenceInstanceService recurrenceInstanceService;
    @Autowired private ApplicationEvents events;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TodoRepository todoRepository;

    private Long userId;
    private Category category;

    @BeforeEach
    void setUp() {
        todoRepository.deleteAll();
        categoryRepository.deleteAll();
        userJpaRepository.deleteAll();

        userId = userJpaRepository.saveAndFlush(
                User.createNewUser(AuthProvider.KAKAO, "event-user", "event@test.com")).getId();
        category = categoryRepository.saveAndFlush(
                Category.builder().name("운동").color(CategoryColor.OR).userId(userId).displayOrder(1L).build());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("투두를 만들면 생성 이벤트를 발행한다")
    void saveTodoPublishesCreated() {
        // given
        TodoSaveRequest request = new TodoSaveRequest();
        ReflectionTestUtils.setField(request, "description", "러닝");
        ReflectionTestUtils.setField(request, "categoryId", category.getId());
        ReflectionTestUtils.setField(request, "date", TODAY);

        // when
        Long todoId = todoService.saveTodo(request, userId).getId();

        // then
        UserEvent event = onlyUserEvent();
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.type()).isEqualTo(EventType.TODO_CREATED);
        assertThat(event.targetId()).isEqualTo(todoId);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("완료하면 완료 이벤트를, 다시 누르면 완료 취소 이벤트를 발행한다")
    void togglePublishesCompletedThenUncompleted() {
        // given
        Long todoId = saveTodo().getId();

        // when
        todoService.toggleTodoCompletion(todoId, userId);
        todoService.toggleTodoCompletion(todoId, userId);

        // then
        assertThat(userEvents()).extracting(UserEvent::type)
                .containsExactly(EventType.TODO_COMPLETED, EventType.TODO_UNCOMPLETED);
        assertThat(userEvents()).allSatisfy(event -> {
            assertThat(event.targetId()).isEqualTo(todoId);
            assertThat(event.properties()).isEqualTo(Map.of("recurring", false));
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("반복 투두를 완료하면 반복 투두로 표시한 완료 이벤트를 발행한다")
    void toggleRecurringTodoMarksRecurring() {
        // given
        Long todoId = saveRecurringTodo().getId();

        // when
        todoService.toggleTodoCompletion(todoId, userId);

        // then
        UserEvent event = lastUserEvent();
        assertThat(event.type()).isEqualTo(EventType.TODO_COMPLETED);
        assertThat(event.properties()).isEqualTo(Map.of("recurring", true));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("반복을 설정하면 주기 종류와 함께 반복 생성 이벤트를 발행한다")
    void createRecurrencePublishesCreated() {
        // given & when
        Todo todo = saveRecurringTodo();

        // then
        UserEvent event = onlyUserEvent();
        assertThat(event.type()).isEqualTo(EventType.RECURRENCE_CREATED);
        assertThat(event.targetId()).isEqualTo(todo.getRecurrenceId());
        assertThat(event.properties()).isEqualTo(Map.of("type", "DAILY"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("반복 회차 하나만 지우면 반복은 유지되므로 투두 삭제 이벤트만 발행한다")
    void deleteThisPublishesTodoDeletedOnly() {
        // given
        Long todoId = saveRecurringTodo().getId();

        // when
        recurrenceInstanceService.delete(todoId, RecurrenceScope.THIS, userId);

        // then
        UserEvent event = lastUserEvent();
        assertThat(event.type()).isEqualTo(EventType.TODO_DELETED);
        assertThat(event.targetId()).isEqualTo(todoId);
        assertThat(event.properties()).isEqualTo(Map.of("recurring", true));
        assertThat(userEvents()).extracting(UserEvent::type).doesNotContain(EventType.RECURRENCE_ENDED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("반복 전체를 지우면 반복 종료 이벤트를 발행한다")
    void deleteAllPublishesRecurrenceEnded() {
        // given
        Todo todo = saveRecurringTodo();

        // when
        recurrenceInstanceService.delete(todo.getId(), RecurrenceScope.ALL, userId);

        // then
        UserEvent event = lastUserEvent();
        assertThat(event.type()).isEqualTo(EventType.RECURRENCE_ENDED);
        assertThat(event.targetId()).isEqualTo(todo.getRecurrenceId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("반복을 해제하면 반복 종료 이벤트를 발행한다")
    void cancelAllPublishesRecurrenceEnded() {
        // given
        Todo todo = saveRecurringTodo();

        // when
        recurrenceInstanceService.cancelRecurrence(todo.getId(), RecurrenceScope.ALL, userId);

        // then
        UserEvent event = lastUserEvent();
        assertThat(event.type()).isEqualTo(EventType.RECURRENCE_ENDED);
        assertThat(event.targetId()).isEqualTo(todo.getRecurrenceId());
    }

    private Todo saveTodo() {
        return todoRepository.saveAndFlush(
                Todo.builder().description("러닝").category(category).date(TODAY).displayOrder(1L).build());
    }

    private Todo saveRecurringTodo() {
        Long todoId = saveTodo().getId();

        RecurrenceCreateRequest request = new RecurrenceCreateRequest();
        ReflectionTestUtils.setField(request, "todoId", todoId);
        ReflectionTestUtils.setField(request, "type", RecurrenceType.DAILY);
        ReflectionTestUtils.setField(request, "startDate", TODAY);
        recurrenceService.createRecurrence(userId, request);

        return todoRepository.findById(todoId).orElseThrow();
    }

    private List<UserEvent> userEvents() {
        return events.stream(UserEvent.class).toList();
    }

    private UserEvent onlyUserEvent() {
        assertThat(userEvents()).hasSize(1);
        return userEvents().get(0);
    }

    private UserEvent lastUserEvent() {
        List<UserEvent> userEvents = userEvents();
        return userEvents.get(userEvents.size() - 1);
    }
}
