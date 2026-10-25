package basakan.fryday.repository.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Slf4j
@Repository
@RequiredArgsConstructor
public class AdminLoginAttemptRepository {

    static final int MAX_ATTEMPTS = 10;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private static final String KEY_PREFIX = "admin:login:attempt:";

    private final StringRedisTemplate stringRedisTemplate;

    // 비밀번호를 확인하기 전에 시도 횟수를 먼저 올려서, 동시 요청으로 제한을 넘기지 못하게 한다.
    // 마지막 시도로부터 잠금 시간이 지나면 횟수가 초기화된다
    public boolean tryAcquire(String loginId) {
        try {
            Long attempts = stringRedisTemplate.opsForValue().increment(key(loginId));
            stringRedisTemplate.expire(key(loginId), LOCK_DURATION);
            return attempts == null || attempts <= MAX_ATTEMPTS;
        } catch (RuntimeException e) {
            // Redis 장애로 팀원이 어드민에 못 들어가는 일이 없도록 잠금 없이 허용한다
            log.warn("어드민 로그인 시도 횟수 확인 실패, 잠금 없이 허용: loginId={}", loginId, e);
            return true;
        }
    }

    public void reset(String loginId) {
        try {
            stringRedisTemplate.delete(key(loginId));
        } catch (RuntimeException e) {
            log.warn("어드민 로그인 시도 횟수 초기화 실패: loginId={}", loginId, e);
        }
    }

    private String key(String loginId) {
        return KEY_PREFIX + loginId;
    }
}
