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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

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
    @DisplayName("scope=ALL 규칙 변경 시 알림이 걸린 미래 인스턴스도 FK 위반 없이 정리된다")
    void editAll_withRuleChange_cleansUpAlarmsBeforeHardDelete() {
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

        // 기존 알림은 todo와 함께 정리되어야 한다 (FK 위반 방지)
        assertThat(todoAlarmRepository.findById(alarmId)).isEmpty();
        // 기존 future 인스턴스는 물리 삭제되어 새 인스턴스가 그 자리를 채운다
        assertThat(todoRepository.findById(futureInstance.getId())).isEmpty();
    }
}
