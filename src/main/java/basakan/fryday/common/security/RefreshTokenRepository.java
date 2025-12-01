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

    public String generateAndSave(Long userId) {
        String refreshToken = UUID.randomUUID().toString();
        String key = KEY_PREFIX + refreshToken;

        redisTemplate.opsForValue().set(
                key,
                String.valueOf(userId),
                refreshTokenExpiration,
                TimeUnit.MILLISECONDS
        );

        return refreshToken;
    }

    public Optional<Long> getUserId(String refreshToken) {
        String key = KEY_PREFIX + refreshToken;
        String userId = redisTemplate.opsForValue().get(key);

        return Optional.ofNullable(userId)
                .map(Long::parseLong);
    }

    public void delete(String refreshToken) {
        String key = KEY_PREFIX + refreshToken;
        redisTemplate.delete(key);
    }
}
