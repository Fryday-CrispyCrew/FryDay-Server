package basakan.fryday.scheduler;

import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.domain.group.GroupPushHistory;
import basakan.fryday.domain.group.GroupPushType;
import basakan.fryday.repository.group.GroupPushHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:group_push_history_cleanup_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        }
)
@Import({JpaConfig.class, GroupPushHistoryCleanupScheduler.class})
@DisplayName("그룹 푸시 발송 기록 정리")
class GroupPushHistoryCleanupSchedulerTest {

    @Autowired private GroupPushHistoryCleanupScheduler scheduler;
    @Autowired private GroupPushHistoryRepository groupPushHistoryRepository;

    @BeforeEach
    void setUp() {
        groupPushHistoryRepository.deleteAll();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("30일이 지난 기록만 지운다")
    void deletesOnlyHistoryOlderThan30Days() {
        // given
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        save(today.minusDays(31));
        save(today.minusDays(30));
        save(today);

        // when
        scheduler.cleanupOldHistory();

        // then
        assertThat(groupPushHistoryRepository.findAll())
                .extracting(GroupPushHistory::getPushDate)
                .containsExactlyInAnyOrder(today.minusDays(30), today);
    }

    private void save(LocalDate pushDate) {
        groupPushHistoryRepository.saveAndFlush(GroupPushHistory.builder()
                .groupId(1L).userId(1L).pushDate(pushDate).type(GroupPushType.GROUP_FRYING_STARTED).build());
    }
}
