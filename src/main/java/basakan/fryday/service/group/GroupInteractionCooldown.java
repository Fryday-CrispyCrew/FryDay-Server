package basakan.fryday.service.group;

import basakan.fryday.domain.group.GroupInteractionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class GroupInteractionCooldown {

    static final Duration COOLDOWN = Duration.ofSeconds(30);

    private final StringRedisTemplate stringRedisTemplate;

    public boolean tryAcquire(Long groupId, Long senderId, Long targetUserId, GroupInteractionType type) {
        String key = key(groupId, senderId, targetUserId, type);
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, "1", COOLDOWN));
        } catch (RuntimeException e) {
            // 쿨다운은 연타를 막는 편의 기능이라, Redis 장애가 상호작용 자체를 막지 않도록 허용한다
            log.warn("상호작용 쿨다운 확인 실패, 쿨다운 없이 허용: key={}", key, e);
            return true;
        }
    }

    static String key(Long groupId, Long senderId, Long targetUserId, GroupInteractionType type) {
        return "group:interaction:" + groupId + ":" + senderId + ":" + targetUserId + ":" + type;
    }
}
