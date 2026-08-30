package basakan.fryday.service.todo;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.common.exception.BusinessException;
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
import org.junit.jupiter.api.BeforeEach;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * spec 4.2.2 알림 정책 재현: 인스턴스 개별 알림(override)과 Master 알림 변경이
 * 실제 발송 대상인 todo_alarms 행에 반영되는지 검증한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:alarm_override_repro;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
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
        RecurrenceOccurrenceCalculator.class,
        TodoAlarmSynchronizer.class,
        basakan.fryday.service.user.UserReadService.class
})
@DisplayName("알림 override 재현 테스트")
class AlarmOverrideReproTest {

    private static final LocalTime MASTER_TIME = LocalTime.of(9, 0);
    private static final LocalTime NEW_TIME = LocalTime.of(15, 0);

    @Autowired private RecurrenceInstanceService service;
    @Autowired private TodoAlarmSynchronizer synchronizer;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private RecurrenceRepository recurrenceRepository;
    @Autowired private TodoRepository todoRepository;
    @Autowired private TodoAlarmRepository todoAlarmRepository;

    private User user;
    private Long userId;
    private Category category;
    private Recurrence master;
    private Todo todayInstance;
    private Todo futureInstance;
    private LocalDate today;
    private LocalDate future;

    @BeforeEach
    void setUp() {
        user = userJpaRepository.save(User.createNewUser(AuthProvider.APPLE, "alarm-repro-" + System.nanoTime(), "alarm@t.com"));
        userId = user.getId();

        category = categoryRepository.save(
                Category.builder().name("업무").color(CategoryColor.BR).userId(userId).displayOrder(1L).build()
        );

        today = LocalDate.now();
        future = today.plusDays(3);

        master = recurrenceRepository.save(Recurrence.builder()
                .userId(userId)
                .categoryId(category.getId())
                .description("반복 작업")
                .type(RecurrenceType.DAILY)
                .frequencyValues(null)
                .startDate(today.minusDays(10))
                .endDate(null)
                .endType(EndType.NONE)
                .notificationTime(MASTER_TIME)
                .lastGeneratedDate(today)
                .build()
        );

        todayInstance = todoRepository.save(Todo.builder()
                .description("반복 작업")
                .category(category)
                .date(today)
                .displayOrder(1L)
                .recurrenceId(master.getId())
                .build()
        );

        futureInstance = todoRepository.save(Todo.builder()
                .description("반복 작업")
                .category(category)
                .date(future)
                .displayOrder(1L)
                .recurrenceId(master.getId())
                .build()
        );

        todoAlarmRepository.save(TodoAlarm.create(todayInstance, user, today.atTime(MASTER_TIME)));
        todoAlarmRepository.save(TodoAlarm.create(futureInstance, user, future.atTime(MASTER_TIME)));
    }

    private Payload payload(String memo, Boolean isAlarmEnabled, LocalTime alarmTime) {
        Payload p = new Payload();
        if (memo != null) ReflectionTestUtils.setField(p, "memo", memo);
        if (isAlarmEnabled != null) ReflectionTestUtils.setField(p, "isAlarmEnabled", isAlarmEnabled);
        if (alarmTime != null) ReflectionTestUtils.setField(p, "alarmTime", alarmTime);
        return p;
    }

    private Optional<TodoAlarm> alarmOf(Todo instance) {
        return todoAlarmRepository.findByTodoId(instance.getId());
    }

    // ── D3: THIS — 개별 알림이 TodoAlarm 행에 반영되어야 한다 ──────────────────

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("[THIS] 개별 알림 시각 변경 시 해당 인스턴스의 TodoAlarm.notifyAt이 바뀐다")
    void editThis_alarmTimeChange_updatesTodoAlarmRow() {
        service.edit(futureInstance.getId(), RecurrenceScope.THIS, payload(null, true, NEW_TIME), userId);

        assertThat(alarmOf(futureInstance)).isPresent();
        assertThat(alarmOf(futureInstance).get().getNotifyAt())
                .as("개별 알림 시각이 실제 발송 행에 반영되어야 함")
                .isEqualTo(future.atTime(NEW_TIME));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("[THIS] 알림 행이 없던 인스턴스에 개별 알림 설정 시 TodoAlarm이 생성된다")
    void editThis_alarmEnable_createsTodoAlarmRow() {
        Todo noAlarmInstance = todoRepository.save(Todo.builder()
                .description("반복 작업")
                .category(category)
                .date(today.plusDays(5))
                .displayOrder(1L)
                .recurrenceId(master.getId())
                .build()
        );

        service.edit(noAlarmInstance.getId(), RecurrenceScope.THIS, payload(null, true, NEW_TIME), userId);

        assertThat(alarmOf(noAlarmInstance)).as("개별 알림 설정 시 발송 행 생성").isPresent();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("[THIS] 알림 끄기(isAlarmEnabled=false) 시 TodoAlarm이 삭제되고 '사용 안 함'이 저장된다")
    void editThis_alarmDisable_deletesTodoAlarmRow() {
        service.edit(futureInstance.getId(), RecurrenceScope.THIS, payload(null, false, null), userId);

        assertThat(alarmOf(futureInstance)).as("알림을 껐으면 발송 행이 없어야 함").isEmpty();

        Todo reloaded = todoRepository.findById(futureInstance.getId()).orElseThrow();
        assertThat(reloaded.getOverrideIsAlarm())
                .as("Master 복귀가 아닌 '사용 안 함' 상태로 저장 (spec 4.2.2)")
                .isFalse();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("[THIS] 지난 시각의 개별 알림은 ALARM_TIME_IN_PAST로 거부된다")
    void editThis_pastAlarmTime_rejected() {
        Todo pastInstance = todoRepository.save(Todo.builder()
                .description("반복 작업")
                .category(category)
                .date(today.minusDays(1))
                .displayOrder(1L)
                .recurrenceId(master.getId())
                .build()
        );

        assertThatThrownBy(() ->
                service.edit(pastInstance.getId(), RecurrenceScope.THIS, payload(null, true, MASTER_TIME), userId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALARM_TIME_IN_PAST);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("[THIS] 빈 payload는 아무 override도 만들지 않는다")
    void editThis_emptyPayload_doesNotCreateOverride() {
        service.edit(futureInstance.getId(), RecurrenceScope.THIS, payload(null, null, null), userId);

        Todo reloaded = todoRepository.findById(futureInstance.getId()).orElseThrow();
        assertThat(reloaded.isOverridden()).as("변경 없는 수정은 override를 만들면 안 됨").isFalse();
        assertThat(alarmOf(futureInstance).get().getNotifyAt())
                .as("알림도 그대로")
                .isEqualTo(future.atTime(MASTER_TIME));
    }

    // ── D4: ALL / THIS_AND_FUTURE — Master 시각 변경이 기존 회차에 전파되어야 한다 ──

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("[ALL] Master 알림 시각 변경 시 상속 중인 기존 회차의 TodoAlarm.notifyAt이 갱신된다")
    void editAll_alarmTimeChange_propagatesToExistingRows() {
        service.edit(todayInstance.getId(), RecurrenceScope.ALL, payload(null, true, NEW_TIME), userId);

        assertThat(recurrenceRepository.findById(master.getId()).orElseThrow().getNotificationTime())
                .as("Master 시각 갱신").isEqualTo(NEW_TIME);
        assertThat(alarmOf(futureInstance).get().getNotifyAt())
                .as("상속 중인 미래 회차의 발송 시각도 갱신되어야 함")
                .isEqualTo(future.atTime(NEW_TIME));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("[ALL] 개별 알림이 설정된 회차는 Master 시각 변경의 영향을 받지 않는다")
    void editAll_alarmTimeChange_preservesOverriddenInstance() {
        LocalTime customTime = LocalTime.of(20, 0);
        Todo overridden = todoRepository.findById(futureInstance.getId()).orElseThrow();
        overridden.applyOverride(null, null, true, customTime);
        todoRepository.saveAndFlush(overridden);
        TodoAlarm customAlarm = alarmOf(futureInstance).orElseThrow();
        customAlarm.changeTime(future.atTime(customTime));
        todoAlarmRepository.saveAndFlush(customAlarm);

        service.edit(todayInstance.getId(), RecurrenceScope.ALL, payload(null, true, NEW_TIME), userId);

        assertThat(alarmOf(futureInstance).get().getNotifyAt())
                .as("개별 알림 회차는 Master 전파에서 제외 (spec 4.2.2)")
                .isEqualTo(future.atTime(customTime));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("[ALL] Master 알림 해제 시 상속 중인 회차의 TodoAlarm이 삭제된다")
    void editAll_alarmDisable_removesInheritedRows() {
        service.edit(todayInstance.getId(), RecurrenceScope.ALL, payload(null, false, null), userId);

        assertThat(recurrenceRepository.findById(master.getId()).orElseThrow().isAlarmEnabled())
                .as("Master 알림 해제").isFalse();
        assertThat(alarmOf(futureInstance))
                .as("상속 중이던 회차의 발송 행 제거")
                .isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("[T&F] 알림 시각 변경 시 이후 회차의 TodoAlarm.notifyAt이 새 시각으로 갱신된다")
    void editThisAndFuture_alarmTimeChange_propagatesToFutureRows() {
        service.edit(todayInstance.getId(), RecurrenceScope.THIS_AND_FUTURE, payload(null, true, NEW_TIME), userId);

        Todo reloadedFuture = todoRepository.findById(futureInstance.getId()).orElseThrow();
        Recurrence newMaster = recurrenceRepository.findById(reloadedFuture.getRecurrenceId()).orElseThrow();
        assertThat(newMaster.getNotificationTime()).as("새 Master 시각").isEqualTo(NEW_TIME);
        assertThat(alarmOf(futureInstance).get().getNotifyAt())
                .as("이후 회차의 발송 시각 갱신")
                .isEqualTo(future.atTime(NEW_TIME));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("[ALL] 재파생 시각이 이미 지났으면 기존 PENDING 알림을 삭제해 오발송을 막는다")
    void editAll_pastEffectiveTime_removesStalePendingAlarm() {
        // 오늘 회차에 20:00 PENDING 알림이 걸려 있고, Master 시각을 이미 지난 00:00으로 변경
        service.edit(todayInstance.getId(), RecurrenceScope.ALL, payload(null, true, LocalTime.MIDNIGHT), userId);

        assertThat(alarmOf(todayInstance))
                .as("이미 지난 시각으로 바뀐 회차의 옛 PENDING 알림은 남아서 울리면 안 됨")
                .isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("[ALL] 재파생 시각이 지났어도 이미 발송된(SENT) 알림 이력은 보존한다")
    void editAll_pastEffectiveTime_preservesSentHistory() {
        TodoAlarm sent = alarmOf(todayInstance).orElseThrow();
        sent.markAsSent();
        todoAlarmRepository.saveAndFlush(sent);

        service.edit(todayInstance.getId(), RecurrenceScope.ALL, payload(null, true, LocalTime.MIDNIGHT), userId);

        assertThat(alarmOf(todayInstance)).as("SENT 이력은 삭제하지 않음").isPresent();
        assertThat(alarmOf(todayInstance).get().getStatus()).isEqualTo(TodoAlarm.AlarmStatus.SENT);
    }

    @Test
    @DisplayName("Synchronizer.sync는 인스턴스 소유자가 아닌 userId를 거부한다")
    void sync_rejectsForeignUserId() {
        User other = userJpaRepository.save(
                User.createNewUser(AuthProvider.APPLE, "alarm-foreign-" + System.nanoTime(), "foreign@t.com"));

        assertThatThrownBy(() -> synchronizer.sync(
                todoRepository.findById(futureInstance.getId()).orElseThrow(),
                other.getId(), NEW_TIME))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("[ALL] 제목만 override된 회차의 알림은 계속 Master를 상속한다 (교차오염 검증)")
    void editAll_titleOverriddenInstance_stillInheritsAlarm() {
        Todo titleOverridden = todoRepository.findById(futureInstance.getId()).orElseThrow();
        titleOverridden.applyOverride("개별 제목", null, null, null);
        todoRepository.saveAndFlush(titleOverridden);

        service.edit(todayInstance.getId(), RecurrenceScope.ALL, payload(null, true, NEW_TIME), userId);

        assertThat(alarmOf(futureInstance).get().getNotifyAt())
                .as("알림은 개별 수정한 적 없으므로 Master 변경을 따라와야 함")
                .isEqualTo(future.atTime(NEW_TIME));
    }
}
