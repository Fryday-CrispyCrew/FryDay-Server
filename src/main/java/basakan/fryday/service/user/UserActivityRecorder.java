package basakan.fryday.service.user;

import basakan.fryday.repository.auth.UserDailyActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 인증된 요청마다 호출되므로, 그날 이미 기록한 유저는 메모리에서 걸러 DB 쓰기를 하루 한 번으로 줄인다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserActivityRecorder {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final UserDailyActivityRepository userDailyActivityRepository;

    private volatile RecordedUsers recordedUsers = new RecordedUsers(LocalDate.MIN);

    public void record(Long userId) {
        record(userId, LocalDate.now(KOREA_ZONE));
    }

    void record(Long userId, LocalDate today) {
        RecordedUsers current = recordedUsersOf(today);
        if (!current.userIds().add(userId)) {
            return;
        }
        try {
            userDailyActivityRepository.insertIgnore(userId, today, LocalDateTime.now(KOREA_ZONE));
        } catch (RuntimeException e) {
            // 활동 기록은 부가 기능이라 실패해도 요청을 막지 않는다. 다음 요청 때 다시 시도한다
            current.userIds().remove(userId);
            log.warn("일별 활동 기록 실패: userId={}, date={}", userId, today, e);
        }
    }

    private RecordedUsers recordedUsersOf(LocalDate today) {
        RecordedUsers current = recordedUsers;
        if (current.date().equals(today)) {
            return current;
        }
        synchronized (this) {
            if (!recordedUsers.date().equals(today)) {
                recordedUsers = new RecordedUsers(today);
            }
            return recordedUsers;
        }
    }

    private record RecordedUsers(LocalDate date, Set<Long> userIds) {
        RecordedUsers(LocalDate date) {
            this(date, ConcurrentHashMap.newKeySet());
        }
    }
}
