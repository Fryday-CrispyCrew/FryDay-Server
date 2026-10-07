package basakan.fryday.service.user;

import basakan.fryday.repository.auth.UserDailyActivityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("일별 활동 기록기")
class UserActivityRecorderTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);

    @Mock private UserDailyActivityRepository userDailyActivityRepository;

    @InjectMocks private UserActivityRecorder recorder;

    @Test
    @DisplayName("같은 날 같은 유저의 요청은 DB에 한 번만 기록한다")
    void recordsOncePerDay() {
        // when
        recorder.record(USER_ID, TODAY);
        recorder.record(USER_ID, TODAY);
        recorder.record(USER_ID, TODAY);

        // then
        then(userDailyActivityRepository).should(times(1))
                .insertIgnore(eq(USER_ID), eq(TODAY), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("날짜가 바뀌면 같은 유저도 다시 기록한다")
    void recordsAgainOnNextDay() {
        // when
        recorder.record(USER_ID, TODAY);
        recorder.record(USER_ID, TODAY.plusDays(1));

        // then
        then(userDailyActivityRepository).should()
                .insertIgnore(eq(USER_ID), eq(TODAY), any(LocalDateTime.class));
        then(userDailyActivityRepository).should()
                .insertIgnore(eq(USER_ID), eq(TODAY.plusDays(1)), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("DB 기록에 실패해도 예외를 던지지 않고, 다음 요청 때 다시 시도한다")
    void retriesAfterFailure() {
        // given
        willThrow(new RuntimeException("DB 장애")).willDoNothing().given(userDailyActivityRepository)
                .insertIgnore(eq(USER_ID), eq(TODAY), any(LocalDateTime.class));

        // when & then
        assertThatCode(() -> recorder.record(USER_ID, TODAY)).doesNotThrowAnyException();
        recorder.record(USER_ID, TODAY);
        recorder.record(USER_ID, TODAY);

        then(userDailyActivityRepository).should(times(2))
                .insertIgnore(eq(USER_ID), eq(TODAY), any(LocalDateTime.class));
    }
}
