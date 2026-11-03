package basakan.fryday.scheduler;

import basakan.fryday.common.service.push.PushService;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.domain.todo.TodoAlarm;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.auth.UserJpaRepository;
import basakan.fryday.repository.todo.TodoAlarmRepository;
import basakan.fryday.repository.todo.TodoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@ExtendWith(MockitoExtension.class)
@DisplayName("투두 푸시 종류")
class TodoPushTypeTest {

    private static final Long USER_ID = 7L;

    @Mock private TodoAlarmRepository todoAlarmRepository;
    @Mock private UserJpaRepository userJpaRepository;
    @Mock private TodoRepository todoRepository;
    @Mock private PushService pushService;

    @InjectMocks private NotificationScheduler notificationScheduler;
    @InjectMocks private DeadlineAlarmScheduler deadlineAlarmScheduler;
    @InjectMocks private BurntAlarmScheduler burntAlarmScheduler;
    @InjectMocks private EncourageAlarmScheduler encourageAlarmScheduler;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.createNewUser(AuthProvider.KAKAO, "push-user", "push@test.com");
        setField(user, "id", USER_ID);
    }

    @Test
    @DisplayName("투두 알람은 종류와 투두 ID 를 담아 보낸다")
    void todoAlarm() {
        // given
        Todo todo = Todo.builder().description("운동").date(LocalDate.now()).displayOrder(1L).build();
        setField(todo, "id", 30L);
        given(todoAlarmRepository.findAllByStatusAndNotifyAtBeforeOrEqual(any(), any()))
                .willReturn(List.of(TodoAlarm.create(todo, user, LocalDateTime.now())));

        // when
        notificationScheduler.sendDueNotifications();

        // then
        then(pushService).should().sendToUser(eq(user), anyString(), anyString(),
                eq(Map.of("type", "TODO_ALARM", "todoId", "30")));
    }

    @Test
    @DisplayName("마감 알림은 종류를 담아 보낸다")
    void deadline() {
        // given
        given(todoRepository.findUserIdsWithTodosByDateAndStatus(any(), any())).willReturn(List.of(USER_ID));
        given(userJpaRepository.findAllById(anyList())).willReturn(List.of(user));

        // when
        deadlineAlarmScheduler.sendDeadlineAlarm();

        // then
        then(pushService).should().sendToUser(eq(user), anyString(), anyString(), eq(Map.of("type", "TODO_DEADLINE")));
    }

    @Test
    @DisplayName("탄 튀김 알림은 종류를 담아 보낸다")
    void burnt() {
        // given
        given(todoRepository.findUserIdsWithTodosByDateAndStatus(any(), any())).willReturn(List.of(USER_ID));
        given(userJpaRepository.findAllById(anyList())).willReturn(List.of(user));

        // when
        burntAlarmScheduler.sendBurntAlarm();

        // then
        then(pushService).should().sendToUser(eq(user), anyString(), anyString(), eq(Map.of("type", "TODO_BURNT")));
    }

    @Test
    @DisplayName("독려 알림은 종류를 담아 보낸다")
    void encourage() {
        // given
        given(userJpaRepository.findAllActiveUserIds()).willReturn(List.of(USER_ID));
        given(todoRepository.findUserIdsWithTodosByDate(any())).willReturn(List.of());
        given(userJpaRepository.findAllById(anyList())).willReturn(List.of(user));

        // when
        encourageAlarmScheduler.sendEncourageAlarm();

        // then
        then(pushService).should().sendToUser(eq(user), anyString(), anyString(), eq(Map.of("type", "TODO_ENCOURAGE")));
    }
}
