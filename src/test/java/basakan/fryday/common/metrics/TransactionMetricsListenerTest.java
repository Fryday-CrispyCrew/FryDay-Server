package basakan.fryday.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionManagerCustomizationAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@ImportAutoConfiguration(TransactionManagerCustomizationAutoConfiguration.class)
@Import({TransactionMetricsListener.class, SimpleMeterRegistry.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("DB 트랜잭션 지표")
class TransactionMetricsListenerTest {

    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private MeterRegistry meterRegistry;

    private TransactionTemplate readTemplate;
    private TransactionTemplate writeTemplate;

    @BeforeEach
    void setUp() {
        readTemplate = new TransactionTemplate(transactionManager);
        readTemplate.setReadOnly(true);
        writeTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    @DisplayName("읽기와 쓰기 트랜잭션의 커밋과 롤백을 나눠 센다")
    void countsByAccessAndResult() {
        // given
        double readCommits = count("read", "commit");
        double writeCommits = count("write", "commit");
        double writeRollbacks = count("write", "rollback");

        // when
        readTemplate.executeWithoutResult(status -> { });
        writeTemplate.executeWithoutResult(status -> { });
        writeTemplate.executeWithoutResult(status -> status.setRollbackOnly());

        // then
        assertThat(count("read", "commit")).isEqualTo(readCommits + 1);
        assertThat(count("write", "commit")).isEqualTo(writeCommits + 1);
        assertThat(count("write", "rollback")).isEqualTo(writeRollbacks + 1);
    }

    @Test
    @DisplayName("바깥 트랜잭션에 참여한 안쪽 호출은 따로 세지 않는다")
    void doesNotCountParticipatingTransactions() {
        // given
        double writeCommits = count("write", "commit");

        // when
        writeTemplate.executeWithoutResult(outer -> writeTemplate.executeWithoutResult(inner -> { }));

        // then
        assertThat(count("write", "commit")).isEqualTo(writeCommits + 1);
    }

    @Test
    @DisplayName("한 번도 세지 않은 조합도 0으로 노출한다")
    void registersAllCountersUpFront() {
        assertThat(meterRegistry.find("db.transactions").counters()).hasSize(4);
    }

    private double count(String access, String result) {
        return meterRegistry.get("db.transactions").tag("access", access).tag("result", result).counter().count();
    }
}
