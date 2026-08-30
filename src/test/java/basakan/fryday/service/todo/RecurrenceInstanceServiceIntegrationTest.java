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

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("개별 수정한 메모는 scope=ALL 내용 수정 후에도 유지되고, 나머지 회차만 새 메모를 반영한다")
    void editThisMemo_survivesEditAll() {
        User user = userJpaRepository.save(User.createNewUser(AuthProvider.APPLE, "memo-keep-sub", "memo-keep@t.com"));
        Long userId = user.getId();

        Category category = categoryRepository.save(
                Category.builder().name("업무").color(CategoryColor.BR).userId(userId).displayOrder(1L).build()
        );

        LocalDate today = LocalDate.now();
        LocalDate dayA = today.plusDays(1);
        LocalDate dayB = today.plusDays(2);

        Recurrence master = recurrenceRepository.save(Recurrence.builder()
                .userId(userId)
                .categoryId(category.getId())
                .description("반복 작업")
                .memo("마스터 메모")
                .type(RecurrenceType.DAILY)
                .frequencyValues(null)
                .startDate(today)
                .endType(EndType.NONE)
                .lastGeneratedDate(today)
                .build()
        );

        // 상속 메모를 가진 두 회차. dayA만 개별 수정한다.
        Todo overridden = todoRepository.save(Todo.builder()
                .description("반복 작업").category(category).date(dayA)
                .displayOrder(1L).recurrenceId(master.getId()).memo("마스터 메모").build()
        );
        Todo plain = todoRepository.save(Todo.builder()
                .description("반복 작업").category(category).date(dayB)
                .displayOrder(1L).recurrenceId(master.getId()).memo("마스터 메모").build()
        );

        // 1) dayA 회차만 메모 개별 수정 (override 기록)
        Payload editThis = new Payload();
        ReflectionTestUtils.setField(editThis, "memo", "개별 메모");
        recurrenceInstanceService.edit(overridden.getId(), RecurrenceScope.THIS, editThis, userId);

        // 2) scope=ALL 내용 수정으로 마스터 메모 변경
        Payload editAll = new Payload();
        ReflectionTestUtils.setField(editAll, "memo", "새 마스터 메모");
        recurrenceInstanceService.edit(plain.getId(), RecurrenceScope.ALL, editAll, userId);

        // then - 개별 수정 회차는 override 유지 (마스터 메모 재상속 안 함)
        Todo reloadedOverridden = todoRepository.findById(overridden.getId()).orElseThrow();
        assertThat(reloadedOverridden.isOverridden()).isTrue();
        assertThat(reloadedOverridden.getOverrideMemo())
                .as("개별 수정 메모는 마스터 전체 수정에도 유지된다")
                .isEqualTo("개별 메모");

        // 개별 수정 안 된 회차만 새 마스터 메모를 반영 (override 회차와 대비)
        Todo reloadedPlain = todoRepository.findById(plain.getId()).orElseThrow();
        assertThat(reloadedPlain.getMemo())
                .as("개별 수정 안 된 회차는 새 마스터 메모를 반영한다")
                .isEqualTo("새 마스터 메모");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("개별 삭제(빈 메모)한 회차는 scope=ALL 수정 후에도 마스터 메모를 재상속하지 않는다")
    void editThisEmptyMemo_notReinheritedAfterEditAll() {
        User user = userJpaRepository.save(User.createNewUser(AuthProvider.APPLE, "memo-del-sub", "memo-del@t.com"));
        Long userId = user.getId();

        Category category = categoryRepository.save(
                Category.builder().name("업무").color(CategoryColor.BR).userId(userId).displayOrder(1L).build()
        );

        LocalDate today = LocalDate.now();
        LocalDate day = today.plusDays(1);

        Recurrence master = recurrenceRepository.save(Recurrence.builder()
                .userId(userId)
                .categoryId(category.getId())
                .description("반복 작업")
                .memo("마스터 메모")
                .type(RecurrenceType.DAILY)
                .frequencyValues(null)
                .startDate(today)
                .endType(EndType.NONE)
                .lastGeneratedDate(today)
                .build()
        );

        Todo instance = todoRepository.save(Todo.builder()
                .description("반복 작업").category(category).date(day)
                .displayOrder(1L).recurrenceId(master.getId()).memo("마스터 메모").build()
        );

        // 1) 메모 개별 삭제 (빈 문자열 → 빈 override)
        Payload delete = new Payload();
        ReflectionTestUtils.setField(delete, "memo", "");
        recurrenceInstanceService.edit(instance.getId(), RecurrenceScope.THIS, delete, userId);

        // 2) scope=ALL 내용 수정으로 마스터 메모 변경
        Payload editAll = new Payload();
        ReflectionTestUtils.setField(editAll, "memo", "새 마스터 메모");
        recurrenceInstanceService.edit(instance.getId(), RecurrenceScope.ALL, editAll, userId);

        // then - 빈 override 유지, 마스터 메모 재상속 안 함
        Todo reloaded = todoRepository.findById(instance.getId()).orElseThrow();
        assertThat(reloaded.isOverridden()).isTrue();
        assertThat(reloaded.getOverrideMemo())
                .as("빈 override는 마스터 전체 수정에도 유지된다")
                .isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("scope=ALL 내용 수정 시 마스터 메모 변경이 유실되지 않고 저장된다")
    void editAll_contentChange_persistsMasterMemo() {
        User user = userJpaRepository.save(User.createNewUser(AuthProvider.APPLE, "master-memo-sub", "master-memo@t.com"));
        Long userId = user.getId();

        Category category = categoryRepository.save(
                Category.builder().name("업무").color(CategoryColor.BR).userId(userId).displayOrder(1L).build()
        );

        LocalDate today = LocalDate.now();
        LocalDate day = today.plusDays(1);

        Recurrence master = recurrenceRepository.save(Recurrence.builder()
                .userId(userId)
                .categoryId(category.getId())
                .description("반복 작업")
                .memo("마스터 메모")
                .type(RecurrenceType.DAILY)
                .frequencyValues(null)
                .startDate(today)
                .endType(EndType.NONE)
                .lastGeneratedDate(today)
                .build()
        );

        Todo instance = todoRepository.save(Todo.builder()
                .description("반복 작업").category(category).date(day)
                .displayOrder(1L).recurrenceId(master.getId()).memo("마스터 메모").build()
        );

        // scope=ALL 내용 수정 (메모 벌크 갱신이 뒤따른다)
        Payload editAll = new Payload();
        ReflectionTestUtils.setField(editAll, "memo", "새 마스터 메모");
        recurrenceInstanceService.edit(instance.getId(), RecurrenceScope.ALL, editAll, userId);

        // 벌크의 컨텍스트 clear로 인해 유실되지 않고 Recurrence 엔티티에도 반영되어야 한다.
        // (미래 회차가 새 메모를 상속하려면 마스터에 저장돼 있어야 함)
        assertThat(recurrenceRepository.findById(master.getId()).orElseThrow().getMemo())
                .as("scope=ALL 내용 수정은 마스터 엔티티에도 반영되어야 한다")
                .isEqualTo("새 마스터 메모");
    }
}
