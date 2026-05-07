package basakan.fryday.service.todo;

import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.todo.request.RecurrenceCreateRequest;
import basakan.fryday.controller.todo.request.RecurrenceUpdateRequest;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.todo.RecurrenceType;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.auth.UserJpaRepository;
import basakan.fryday.repository.todo.TodoRepository;
import basakan.fryday.service.user.UserReadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:recurrence_service_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
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
        RecurrenceService.class,
        TodoService.class,
        RecurrenceOccurrenceMaterializeService.class,
        RecurrenceOccurrenceCalculator.class,
        UserReadService.class
})
@DisplayName("RecurrenceService 통합")
class RecurrenceServiceIntegrationTest {

    private static final LocalDate ANCHOR = LocalDate.of(2026, 4, 1);
    private static final LocalDate SECOND_DAY = LocalDate.of(2026, 4, 2);
    private static final LocalDate RANGE_END = LocalDate.of(2026, 4, 5);

    @Autowired
    private RecurrenceService recurrenceService;
    @Autowired
    private TodoService todoService;
    @Autowired
    private RecurrenceOccurrenceMaterializeService materializeService;

    @Autowired
    private UserJpaRepository userJpaRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private TodoRepository todoRepository;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("규칙 수정 후 앵커 날짜에 materialize 하면 투두가 다시 생긴다")
    void anchorDate_materializesAgain_afterRecurrenceUpdate() {
        User user = userJpaRepository.save(User.createNewUser(AuthProvider.APPLE, "dev-sub", "t@t.com"));
        Long userId = user.getId();

        Category category = categoryRepository.save(
                Category.builder().name("업무").color(CategoryColor.BR).userId(userId).displayOrder(1L).build()
        );

        Todo anchorTodo = todoRepository.save(
                Todo.builder().description("반복 A").category(category).date(ANCHOR).displayOrder(1L).build()
        );

        RecurrenceCreateRequest createRequest = new RecurrenceCreateRequest();
        ReflectionTestUtils.setField(createRequest, "todoId", anchorTodo.getId());
        ReflectionTestUtils.setField(createRequest, "type", RecurrenceType.DAILY);
        ReflectionTestUtils.setField(createRequest, "startDate", ANCHOR);
        ReflectionTestUtils.setField(createRequest, "endDate", RANGE_END);
        ReflectionTestUtils.setField(createRequest, "notificationTime", null);

        recurrenceService.createRecurrence(userId, createRequest);

        Todo updatedAnchor = todoRepository.findById(anchorTodo.getId()).orElseThrow();
        Long recurrenceId = updatedAnchor.getRecurrenceId();
        assertThat(recurrenceId).isNotNull();

        RecurrenceUpdateRequest updateRequest = new RecurrenceUpdateRequest();
        ReflectionTestUtils.setField(updateRequest, "type", RecurrenceType.DAILY);
        ReflectionTestUtils.setField(updateRequest, "startDate", ANCHOR);
        ReflectionTestUtils.setField(updateRequest, "endDate", LocalDate.of(2026, 4, 10));
        ReflectionTestUtils.setField(updateRequest, "notificationTime", null);

        recurrenceService.updateRecurrence(recurrenceId, updateRequest, userId);

        assertThat(todoRepository.findAllByUserIdAndDate(userId, ANCHOR)).isEmpty();

        Todo materialized = materializeService.materializeOccurrenceIfNotExists(userId, recurrenceId, ANCHOR);

        assertThat(materialized.getDate()).isEqualTo(ANCHOR);
        assertThat(materialized.getRecurrenceId()).isEqualTo(recurrenceId);
        assertThat(todoRepository.findAllByUserIdAndDate(userId, ANCHOR)).hasSize(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("삭제된 인스턴스는 soft delete 상태로 DB에 남아 재생성되지 않는다")
    void deletedOccurrence_notRegenerated_afterDeletion() {
        User user = userJpaRepository.save(User.createNewUser(AuthProvider.APPLE, "dev-sub-3", "t3@t.com"));
        Long userId = user.getId();

        Category category = categoryRepository.save(
                Category.builder().name("업무2").color(CategoryColor.BR).userId(userId).displayOrder(1L).build()
        );

        Todo anchorTodo = todoRepository.save(
                Todo.builder().description("반복 C").category(category).date(ANCHOR).displayOrder(1L).build()
        );

        RecurrenceCreateRequest createRequest = new RecurrenceCreateRequest();
        ReflectionTestUtils.setField(createRequest, "todoId", anchorTodo.getId());
        ReflectionTestUtils.setField(createRequest, "type", RecurrenceType.DAILY);
        ReflectionTestUtils.setField(createRequest, "startDate", ANCHOR);
        ReflectionTestUtils.setField(createRequest, "endDate", RANGE_END);
        ReflectionTestUtils.setField(createRequest, "notificationTime", null);

        recurrenceService.createRecurrence(userId, createRequest);

        Todo refreshedAnchor = todoRepository.findById(anchorTodo.getId()).orElseThrow();
        Long recurrenceId = refreshedAnchor.getRecurrenceId();

        // SECOND_DAY 인스턴스 생성
        materializeService.materializeOccurrenceIfNotExists(userId, recurrenceId, SECOND_DAY);
        Todo secondDayTodo = todoRepository.findAllByUserIdAndDate(userId, SECOND_DAY).stream()
                .filter(t -> recurrenceId.equals(t.getRecurrenceId()))
                .findFirst()
                .orElseThrow();

        // 인스턴스 삭제 (soft delete)
        todoService.deleteTodo(secondDayTodo.getId(), userId);

        // 삭제 후 목록 조회 시 비어있음
        assertThat(todoRepository.findAllByUserIdAndDate(userId, SECOND_DAY)).isEmpty();

        // 재생성 시도 → 삭제된 인스턴스가 DB에 존재하므로 null 반환 (SKIP)
        Todo result = materializeService.materializeOccurrenceIfNotExists(userId, recurrenceId, SECOND_DAY);
        assertThat(result).isNull();

        // 여전히 목록에 노출되지 않음
        assertThat(todoRepository.findAllByUserIdAndDate(userId, SECOND_DAY)).isEmpty();
    }
}
