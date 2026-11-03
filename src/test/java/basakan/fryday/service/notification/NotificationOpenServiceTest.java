package basakan.fryday.service.notification;

import basakan.fryday.controller.notification.request.NotificationOpenRequest;
import basakan.fryday.domain.eventlog.EventType;
import basakan.fryday.service.eventlog.UserEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("알림 오픈 기록")
class NotificationOpenServiceTest {

    private static final Long USER_ID = 1L;

    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private NotificationOpenService notificationOpenService;

    @Test
    @DisplayName("그룹 푸시를 열면 그룹 ID 를 대상으로, 종류와 열린 상황을 함께 기록한다")
    void recordGroupPushOpen() {
        // when
        notificationOpenService.recordOpen(new NotificationOpenRequest("background", "GROUP_JOINED", 12L), USER_ID);

        // then
        UserEvent event = publishedEvent();
        assertThat(event.userId()).isEqualTo(USER_ID);
        assertThat(event.type()).isEqualTo(EventType.NOTIFICATION_OPENED);
        assertThat(event.targetId()).isEqualTo(12L);
        assertThat(event.properties()).isEqualTo(Map.of("type", "GROUP_JOINED", "trigger", "background"));
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("종류와 그룹 ID 가 없는 알림은 열린 상황만 기록한다")
    void recordOpenWithoutTypeAndGroup() {
        // when
        notificationOpenService.recordOpen(new NotificationOpenRequest("cold_start", null, null), USER_ID);

        // then
        UserEvent event = publishedEvent();
        assertThat(event.targetId()).isNull();
        assertThat(event.properties()).isEqualTo(Map.of("trigger", "cold_start"));
    }

    private UserEvent publishedEvent() {
        ArgumentCaptor<UserEvent> captor = ArgumentCaptor.forClass(UserEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        return captor.getValue();
    }
}
