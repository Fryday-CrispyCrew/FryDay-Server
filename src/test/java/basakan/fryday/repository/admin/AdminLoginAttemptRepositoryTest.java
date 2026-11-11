package basakan.fryday.repository.admin;

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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DisplayName("어드민 로그인 시도 횟수")
class AdminLoginAttemptRepositoryTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private final AdminLoginAttemptRepository repository = new AdminLoginAttemptRepository(redisTemplate);

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
    @DisplayName("10번까지 허용하고 11번째부터 막으며, 잠금 시간이 만료로 걸린다")
    void locksAfterMaxAttempts() {
        // when
        for (int i = 0; i < AdminLoginAttemptRepository.MAX_ATTEMPTS; i++) {
            assertThat(repository.tryAcquire("admin")).isTrue();
        }

        // then
        assertThat(repository.tryAcquire("admin")).isFalse();
        assertThat(redisTemplate.getExpire("admin:login:attempt:admin"))
                .isPositive()
                .isLessThanOrEqualTo(AdminLoginAttemptRepository.LOCK_DURATION.toSeconds());
    }

    @Test
    @DisplayName("동시에 시도해도 허용되는 횟수는 10번을 넘지 않는다")
    void concurrentAttempts() throws Exception {
        // given
        Callable<Boolean> attempt = () -> repository.tryAcquire("admin");
        ExecutorService executor = Executors.newFixedThreadPool(10);

        // when
        long allowed;
        try {
            var futures = executor.invokeAll(IntStream.range(0, 20).mapToObj(i -> attempt).toList());
            allowed = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    allowed++;
                }
            }
        } finally {
            executor.shutdown();
        }

        // then
        assertThat(allowed).isEqualTo(AdminLoginAttemptRepository.MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("초기화하면 다시 시도할 수 있고, 다른 아이디에는 영향이 없다")
    void resetUnlocks() {
        // given
        for (int i = 0; i <= AdminLoginAttemptRepository.MAX_ATTEMPTS; i++) {
            repository.tryAcquire("admin");
        }
        repository.tryAcquire("other");

        // when
        repository.reset("admin");

        // then
        assertThat(repository.tryAcquire("admin")).isTrue();
        assertThat(redisTemplate.opsForValue().get("admin:login:attempt:other")).isEqualTo("1");
    }
}
