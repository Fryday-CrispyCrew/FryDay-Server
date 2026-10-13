package basakan.fryday.repository.eventlog;

import basakan.fryday.domain.eventlog.EventLog;
import basakan.fryday.domain.eventlog.EventType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:event_log_repository_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        }
)
@DisplayName("이벤트 로그 리포지토리")
class EventLogRepositoryTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 13, 9, 30);

    @Autowired private EventLogRepository eventLogRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("부가 정보를 JSON 으로 저장하고 그대로 읽어온다")
    void savesAndReadsProperties() {
        // given
        Long id = eventLogRepository.saveAndFlush(EventLog.builder()
                .userId(1L)
                .eventType(EventType.RECURRENCE_CREATED)
                .targetId(7L)
                .properties(Map.of("type", "WEEKLY", "recurring", true))
                .occurredAt(OCCURRED_AT)
                .build()).getId();
        entityManager.clear();

        // when
        EventLog found = eventLogRepository.findById(id).orElseThrow();

        // then
        assertThat(found.getEventType()).isEqualTo(EventType.RECURRENCE_CREATED);
        assertThat(found.getTargetId()).isEqualTo(7L);
        assertThat(found.getProperties()).containsEntry("type", "WEEKLY").containsEntry("recurring", true);
        assertThat(found.getOccurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    @DisplayName("대상과 부가 정보가 없는 이벤트도 저장된다")
    void savesWithoutTargetAndProperties() {
        // given
        Long id = eventLogRepository.saveAndFlush(EventLog.builder()
                .userId(1L)
                .eventType(EventType.USER_SIGNED_UP)
                .occurredAt(OCCURRED_AT)
                .build()).getId();
        entityManager.clear();

        // when
        EventLog found = eventLogRepository.findById(id).orElseThrow();

        // then
        assertThat(found.getTargetId()).isNull();
        assertThat(found.getProperties()).isNull();
    }
}
