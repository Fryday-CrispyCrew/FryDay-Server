package basakan.fryday.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;

// Spring Boot 가 이 빈을 트랜잭션 매니저에 자동으로 붙인다. 콜백은 새로 시작한 트랜잭션에만 불린다
@Component
public class TransactionMetricsListener implements TransactionExecutionListener {

    private static final String METRIC_NAME = "db.transactions";

    private final Counter readCommits;
    private final Counter writeCommits;
    private final Counter readRollbacks;
    private final Counter writeRollbacks;

    // 처음 셀 때 만들면 increase() 에서 첫 증가분이 빠지므로 미리 등록해 0부터 노출한다
    public TransactionMetricsListener(MeterRegistry meterRegistry) {
        this.readCommits = counter(meterRegistry, "read", "commit");
        this.writeCommits = counter(meterRegistry, "write", "commit");
        this.readRollbacks = counter(meterRegistry, "read", "rollback");
        this.writeRollbacks = counter(meterRegistry, "write", "rollback");
    }

    @Override
    public void afterCommit(TransactionExecution transaction, Throwable commitFailure) {
        if (commitFailure == null) {
            (transaction.isReadOnly() ? readCommits : writeCommits).increment();
        }
    }

    @Override
    public void afterRollback(TransactionExecution transaction, Throwable rollbackFailure) {
        (transaction.isReadOnly() ? readRollbacks : writeRollbacks).increment();
    }

    private static Counter counter(MeterRegistry meterRegistry, String access, String result) {
        return Counter.builder(METRIC_NAME)
                .description("새로 시작해 끝난 DB 트랜잭션 수")
                .tag("access", access)
                .tag("result", result)
                .register(meterRegistry);
    }
}
