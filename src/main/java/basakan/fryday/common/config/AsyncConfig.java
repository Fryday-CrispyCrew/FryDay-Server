package basakan.fryday.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

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
    public Executor eventLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("event-log-");
        executor.initialize();
        return executor;
    }
}
