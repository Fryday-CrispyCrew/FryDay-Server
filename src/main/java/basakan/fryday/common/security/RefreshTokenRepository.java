package basakan.fryday.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "refreshToken:";
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    public String generateAndSave(Long userId, String deviceId) {
        String refreshToken = UUID.randomUUID().toString();
        String key = KEY_PREFIX + refreshToken;

        // userId와 deviceId를 함께 저장 (형식: "userId:deviceId")
        String value = userId + ":" + deviceId;

        redisTemplate.opsForValue().set(
                key,
                value,
                refreshTokenExpiration,
                TimeUnit.MILLISECONDS
        );

        return refreshToken;
    }

    public Optional<Long> getUserId(String refreshToken) {
        String key = KEY_PREFIX + refreshToken;
        String value = redisTemplate.opsForValue().get(key);

        return Optional.ofNullable(value)
                .map(v -> v.split(":")[0])
                .map(Long::parseLong);
    }

    public Optional<String> getDeviceId(String refreshToken) {
        String key = KEY_PREFIX + refreshToken;
        String value = redisTemplate.opsForValue().get(key);

        return Optional.ofNullable(value)
                .map(v -> v.split(":"))
                .filter(parts -> parts.length > 1)
                .map(parts -> parts[1]);
    }

    public void delete(String refreshToken) {
        String key = KEY_PREFIX + refreshToken;
        redisTemplate.delete(key);
    }
}
