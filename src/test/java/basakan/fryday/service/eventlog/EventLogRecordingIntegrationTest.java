package basakan.fryday.service.eventlog;

import basakan.fryday.common.config.AsyncConfig;
import basakan.fryday.domain.eventlog.EventLog;
import basakan.fryday.domain.eventlog.EventType;
import basakan.fryday.repository.eventlog.EventLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:event_log_recording_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        }
)
@Import({AsyncConfig.class, EventLogListener.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("이벤트 로그 기록")
class EventLogRecordingIntegrationTest {

    private static final long WAIT_MILLIS = 2000;

    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private EventLogRepository eventLogRepository;

    @BeforeEach
    void setUp() {
        eventLogRepository.deleteAll();
    }

    @Test
    @DisplayName("트랜잭션이 커밋되면 이벤트 로그가 저장된다")
    void recordsAfterCommit() throws InterruptedException {
        // when
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(
                UserEvent.of(1L, EventType.TODO_COMPLETED, 10L, Map.of("recurring", true))));

        // then
        List<EventLog> logs = waitForLogs(1);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getEventType()).isEqualTo(EventType.TODO_COMPLETED);
        assertThat(logs.get(0).getProperties()).containsEntry("recurring", true);
    }

    @Test
    @DisplayName("트랜잭션이 롤백되면 이벤트 로그를 남기지 않는다")
    void doesNotRecordAfterRollback() throws InterruptedException {
        // when — 저장은 발생 순서대로 되므로, 뒤의 커밋된 이벤트가 저장됐다면 앞의 롤백된 이벤트는 건너뛴 것이다
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(UserEvent.of(1L, EventType.TODO_CREATED, 10L));
            status.setRollbackOnly();
        });
        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(UserEvent.of(1L, EventType.USER_WITHDREW)));

        // then
        assertThat(waitForLogs(1)).extracting(EventLog::getEventType).containsExactly(EventType.USER_WITHDREW);
    }

    private List<EventLog> waitForLogs(int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (eventLogRepository.count() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        return eventLogRepository.findAll();
    }
}
