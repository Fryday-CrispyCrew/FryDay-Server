package basakan.fryday.common.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("비동기 실행기 설정")
class AsyncConfigTest {

    @Test
    @DisplayName("이벤트 로그 대기열이 가득 차면 버린 수를 지표로 남기고, 기존처럼 거부 예외를 던진다")
    void eventLogExecutorCountsRejections() throws InterruptedException {
        // given
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new AsyncConfig().eventLogExecutor(registry);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);

        assertThat(registry.get("event.log.rejected").counter().count()).isZero();

        try {
            executor.execute(() -> {
                started.countDown();
                awaitQuietly(release);
            });
            started.await();
            for (int i = 0; i < executor.getQueueCapacity(); i++) {
                executor.execute(() -> { });
            }

            // when & then
            assertThatThrownBy(() -> executor.execute(() -> { })).isInstanceOf(TaskRejectedException.class);
            assertThat(registry.get("event.log.rejected").counter().count()).isEqualTo(1);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("서버 종료 중에 거부된 이벤트는 대기열 넘침으로 세지 않는다")
    void eventLogExecutorDoesNotCountRejectionsAfterShutdown() {
        // given
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new AsyncConfig().eventLogExecutor(registry);
        executor.shutdown();

        // when & then
        assertThatThrownBy(() -> executor.execute(() -> { })).isInstanceOf(TaskRejectedException.class);
        assertThat(registry.get("event.log.rejected").counter().count()).isZero();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
