package basakan.fryday.service.eventlog;

import basakan.fryday.domain.eventlog.EventLog;
import basakan.fryday.domain.eventlog.EventType;
import basakan.fryday.repository.eventlog.EventLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("이벤트 로그 리스너")
class EventLogListenerTest {

    @Mock private EventLogRepository eventLogRepository;

    @InjectMocks private EventLogListener eventLogListener;

    @Captor private ArgumentCaptor<EventLog> eventLogCaptor;

    @Test
    @DisplayName("유저 이벤트를 받으면 발생 시각 그대로 이벤트 로그에 저장한다")
    void savesEventLog() {
        // given
        UserEvent event = UserEvent.of(1L, EventType.TODO_COMPLETED, 10L, Map.of("recurring", true));

        // when
        eventLogListener.onUserEvent(event);

        // then
        then(eventLogRepository).should().save(eventLogCaptor.capture());
        EventLog saved = eventLogCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getEventType()).isEqualTo(EventType.TODO_COMPLETED);
        assertThat(saved.getTargetId()).isEqualTo(10L);
        assertThat(saved.getProperties()).containsEntry("recurring", true);
        assertThat(saved.getOccurredAt()).isEqualTo(event.occurredAt());
    }

    @Test
    @DisplayName("저장에 실패해도 예외를 밖으로 던지지 않는다")
    void swallowsSaveFailure() {
        // given
        given(eventLogRepository.save(any())).willThrow(new IllegalStateException("DB 장애"));

        // when & then
        assertThatCode(() -> eventLogListener.onUserEvent(UserEvent.of(1L, EventType.USER_SIGNED_UP)))
                .doesNotThrowAnyException();
    }
}
