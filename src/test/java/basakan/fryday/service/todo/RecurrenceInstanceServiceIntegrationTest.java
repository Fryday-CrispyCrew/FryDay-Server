package basakan.fryday.service.todo;

import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.controller.todo.request.InstanceEditRequest.Payload;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.todo.EndType;
import basakan.fryday.domain.todo.Recurrence;
import basakan.fryday.domain.todo.RecurrenceScope;
import basakan.fryday.domain.todo.RecurrenceType;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.domain.todo.TodoAlarm;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.auth.UserJpaRepository;
import basakan.fryday.repository.todo.RecurrenceRepository;
import basakan.fryday.repository.todo.TodoAlarmRepository;
import basakan.fryday.repository.todo.TodoRepository;
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
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:recurrence_instance_service_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
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
        RecurrenceInstanceService.class,
        RecurrenceOccurrenceCalculator.class
})
@DisplayName("RecurrenceInstanceService 통합")
class RecurrenceInstanceServiceIntegrationTest {

    @Autowired private RecurrenceInstanceService recurrenceInstanceService;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private RecurrenceRepository recurrenceRepository;
    @Autowired private TodoRepository todoRepository;
    @Autowired private TodoAlarmRepository todoAlarmRepository;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("scope=ALL 규칙 변경 시 새 규칙에도 해당하는 회차는 알림과 함께 유지된다")
    void editAll_withRuleChange_keepsStillMatchingInstances() {
        User user = userJpaRepository.save(User.createNewUser(AuthProvider.APPLE, "fk-bug-sub", "fk@t.com"));
        Long userId = user.getId();

        Category category = categoryRepository.save(
                Category.builder().name("업무").color(CategoryColor.BR).userId(userId).displayOrder(1L).build()
        );

        LocalDate today = LocalDate.now();
        LocalDate future = today.plusDays(3);

        Recurrence master = recurrenceRepository.save(Recurrence.builder()
                .userId(userId)
                .categoryId(category.getId())
                .description("반복 작업")
                .type(RecurrenceType.DAILY)
                .frequencyValues(null)
                .startDate(today)
                .endDate(today.plusDays(30))
                .endType(EndType.UNTIL)
                .lastGeneratedDate(today.plusDays(30))
                .build()
        );

        Todo futureInstance = todoRepository.save(Todo.builder()
                .description("반복 작업")
                .category(category)
                .date(future)
                .displayOrder(1L)
                .recurrenceId(master.getId())
                .build()
        );

        Long alarmId = todoAlarmRepository.save(
                TodoAlarm.create(futureInstance, user, future.atTime(9, 0))
        ).getId();

        Payload payload = new Payload();
        org.springframework.test.util.ReflectionTestUtils.setField(payload, "endDate", today.plusDays(60));

        assertThatCode(() ->
                recurrenceInstanceService.edit(futureInstance.getId(), RecurrenceScope.ALL, payload, userId)
        ).doesNotThrowAnyException();

        // DAILY 규칙은 그대로이고 종료일만 늘어났으므로 이 회차는 여전히 규칙에 해당한다.
        // 지우고 다시 만들지 않으므로 행과 알림이 그대로 살아있어야 한다.
        assertThat(todoRepository.findById(futureInstance.getId()))
                .as("새 규칙에도 해당하는 회차는 유지되어야 한다")
                .isPresent()
                .get()
                .satisfies(t -> assertThat(t.isDeleted()).isFalse());

        assertThat(todoAlarmRepository.findById(alarmId))
                .as("회차가 유지되므로 알림도 함께 유지되어야 한다")
                .isPresent();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("scope=ALL 규칙 변경 시 새 규칙에서 벗어난 회차는 정리된다")
    void editAll_withRuleChange_dropsInstancesOutsideNewRule() {
        User user = userJpaRepository.save(User.createNewUser(AuthProvider.APPLE, "drop-sub", "drop@t.com"));
        Long userId = user.getId();

        Category category = categoryRepository.save(
                Category.builder().name("업무").color(CategoryColor.BR).userId(userId).displayOrder(1L).build()
        );

        LocalDate today = LocalDate.now();
        LocalDate target = today.plusDays(3);

        Recurrence master = recurrenceRepository.save(Recurrence.builder()
                .userId(userId)
                .categoryId(category.getId())
                .description("매일 보고")
                .type(RecurrenceType.DAILY)
                .frequencyValues(null)
                .startDate(today)
                .endType(EndType.NONE)
                .lastGeneratedDate(today)
                .build()
        );

        Todo instance = todoRepository.save(Todo.builder()
                .description("매일 보고")
                .category(category)
                .date(target)
                .displayOrder(1L)
                .recurrenceId(master.getId())
                .build()
        );

        // 종료일을 target 이전으로 당겨 이 회차가 규칙에서 벗어나게 만든다
        Payload payload = new Payload();
        ReflectionTestUtils.setField(payload, "endDate", target.minusDays(1));

        recurrenceInstanceService.edit(instance.getId(), RecurrenceScope.ALL, payload, userId);

        assertThat(todoRepository.findAllByUserIdAndDate(userId, target))
                .as("새 규칙에서 벗어난 회차는 목록에서 사라져야 한다")
                .isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("THIS_AND_FUTURE 수정 후에도 이후 회차의 알림과 순서가 유지된다")
    void editThisAndFuture_preservesAlarmAndDisplayOrder() {
        User user = userJpaRepository.save(User.createNewUser(AuthProvider.APPLE, "alarm-loss-sub", "alarm@t.com"));
        Long userId = user.getId();

        Category category = categoryRepository.save(
                Category.builder().name("운동").color(CategoryColor.BR).userId(userId).displayOrder(1L).build()
        );

        LocalDate today = LocalDate.now();
        LocalDate target = today.plusDays(3);

        Recurrence master = recurrenceRepository.save(Recurrence.builder()
                .userId(userId)
                .categoryId(category.getId())
                .description("스트레칭")
                .type(RecurrenceType.DAILY)
                .frequencyValues(null)
                .startDate(today)
                .endType(EndType.NONE)
                .notificationTime(LocalTime.of(9, 0))
                .lastGeneratedDate(today)
                .build()
        );

        // 알림이 걸린 미래 회차. displayOrder는 목록 두 번째 자리
        Todo instance = todoRepository.save(Todo.builder()
                .description("스트레칭")
                .category(category)
                .date(target)
                .displayOrder(2L)
                .recurrenceId(master.getId())
                .build()
        );
        todoAlarmRepository.save(TodoAlarm.create(instance, user, target.atTime(9, 0)));

        Payload payload = new Payload();
        ReflectionTestUtils.setField(payload, "title", "가벼운 스트레칭");

        recurrenceInstanceService.edit(instance.getId(), RecurrenceScope.THIS_AND_FUTURE, payload, userId);

        // 수정 후에도 해당 날짜에 살아있는 회차가 하나 있어야 한다
        List<Todo> survivors = todoRepository.findAllByUserIdAndDate(userId, target);
        assertThat(survivors).hasSize(1);

        Todo survivor = survivors.get(0);

        // 알림이 유지되어야 한다 — 현재는 generateInstances가 TodoAlarm을 만들지 않아 실패한다
        assertThat(todoAlarmRepository.findByTodoId(survivor.getId()))
                .as("반복 수정 후에도 이후 회차의 알림이 유지되어야 한다")
                .isPresent();

        // 목록 내 순서가 유지되어야 한다
        assertThat(survivor.getDisplayOrder())
                .as("재생성된 회차의 displayOrder가 유지되어야 한다")
                .isEqualTo(2L);
    }
}
