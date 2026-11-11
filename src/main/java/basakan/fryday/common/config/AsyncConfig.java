package basakan.fryday.common.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("reportAsyncExecutor")
    public Executor reportAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(15);
        executor.setQueueCapacity(30);
        executor.setThreadNamePrefix("report-async-");
        executor.initialize();
        return executor;
    }

    @Bean("pushAsyncExecutor")
    public Executor pushAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("push-async-");
        executor.initialize();
        return executor;
    }

    // 스레드를 하나만 둬서 이벤트가 발생한 순서대로 저장되게 한다
    @Bean("eventLogExecutor")
    public Executor eventLogExecutor(MeterRegistry meterRegistry) {
        // 처음 버릴 때 만들면 increase() 에서 첫 증가분이 빠지므로 미리 등록해 0부터 노출한다
        Counter rejected = Counter.builder("event.log.rejected")
                .description("저장 대기열이 가득 차 버린 이벤트 로그 수")
                .register(meterRegistry);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("event-log-");
        executor.setRejectedExecutionHandler((task, pool) -> {
            if (pool.isShutdown()) {
                log.warn("서버 종료 중이라 이벤트 로그를 저장하지 못했습니다");
                throw new RejectedExecutionException("이벤트 로그 실행기가 종료되었습니다");
            }
            rejected.increment();
            log.error("이벤트 로그 저장 대기열이 가득 차 이벤트를 버립니다: queued={}", pool.getQueue().size());
            throw new RejectedExecutionException("이벤트 로그 저장 대기열이 가득 찼습니다");
        });
        executor.initialize();
        return executor;
    }
}
