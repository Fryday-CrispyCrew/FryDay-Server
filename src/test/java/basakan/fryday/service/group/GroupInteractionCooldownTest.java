package basakan.fryday.service.group;

import basakan.fryday.domain.group.GroupInteractionType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static basakan.fryday.domain.group.GroupInteractionType.APPLAUSE;
import static basakan.fryday.domain.group.GroupInteractionType.KNOCK;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DisplayName("그룹 상호작용 쿨다운")
class GroupInteractionCooldownTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private final GroupInteractionCooldown cooldown = new GroupInteractionCooldown(redisTemplate);

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void flush() {
        redisTemplate.execute(connection -> {
            connection.serverCommands().flushAll();
            return null;
        }, true);
    }

    @Test
    @DisplayName("처음 보내면 통과하고, 같은 버튼은 30초 동안 막힌다")
    void blocksSameButtonFor30Seconds() {
        // when
        boolean first = cooldown.tryAcquire(1L, 10L, 20L, KNOCK);
        boolean second = cooldown.tryAcquire(1L, 10L, 20L, KNOCK);

        // then
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(redisTemplate.getExpire(GroupInteractionCooldown.key(1L, 10L, 20L, KNOCK)))
                .isBetween(1L, GroupInteractionCooldown.COOLDOWN.toSeconds());
    }

    @Test
    @DisplayName("버튼, 받는 사람, 그룹 중 하나라도 다르면 바로 보낼 수 있다")
    void differentKeysAreIndependent() {
        // given
        cooldown.tryAcquire(1L, 10L, 20L, KNOCK);

        // when & then
        assertThat(cooldown.tryAcquire(1L, 10L, 20L, APPLAUSE)).isTrue();
        assertThat(cooldown.tryAcquire(1L, 10L, 30L, KNOCK)).isTrue();
        assertThat(cooldown.tryAcquire(2L, 10L, 20L, KNOCK)).isTrue();
    }

    @Test
    @DisplayName("Redis 에 연결할 수 없으면 쿨다운 없이 허용한다")
    void allowsWhenRedisIsUnavailable() {
        // given
        LettuceConnectionFactory unreachable = new LettuceConnectionFactory("localhost", 1);
        unreachable.afterPropertiesSet();
        unreachable.start();
        GroupInteractionCooldown brokenCooldown = new GroupInteractionCooldown(new StringRedisTemplate(unreachable));

        try {
            // when & then
            assertThat(brokenCooldown.tryAcquire(1L, 10L, 20L, GroupInteractionType.ORDER)).isTrue();
        } finally {
            unreachable.destroy();
        }
    }
}
