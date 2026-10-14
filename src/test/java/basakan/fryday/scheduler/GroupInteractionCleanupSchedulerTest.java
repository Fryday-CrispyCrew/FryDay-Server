package basakan.fryday.scheduler;

import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.domain.group.GroupInteraction;
import basakan.fryday.domain.group.GroupInteractionType;
import basakan.fryday.repository.group.GroupInteractionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:group_interaction_cleanup_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        }
)
@Import({JpaConfig.class, GroupInteractionCleanupScheduler.class})
@DisplayName("그룹 상호작용 기록 정리")
class GroupInteractionCleanupSchedulerTest {

    @Autowired private GroupInteractionCleanupScheduler scheduler;
    @Autowired private GroupInteractionRepository groupInteractionRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        groupInteractionRepository.deleteAll();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("1년이 지난 기록만 지운다")
    void deletesOnlyInteractionsOlderThanOneYear() {
        // given
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        Long expired = saveAt(today.minusYears(1).minusDays(1).atTime(23, 59));
        Long boundary = saveAt(today.minusYears(1).atStartOfDay());
        Long monthOld = saveAt(today.minusDays(31).atTime(9, 0));
        Long recent = saveAt(today.atTime(9, 0));

        // when
        scheduler.cleanupOldInteractions();

        // then
        assertThat(groupInteractionRepository.findAll())
                .extracting(GroupInteraction::getId)
                .containsExactlyInAnyOrder(boundary, monthOld, recent)
                .doesNotContain(expired);
    }

    private Long saveAt(LocalDateTime createdAt) {
        Long id = groupInteractionRepository.saveAndFlush(GroupInteraction.builder()
                .groupId(1L).senderId(1L).targetId(2L).type(GroupInteractionType.KNOCK).build()).getId();
        jdbcTemplate.update("UPDATE group_interaction SET created_at = ? WHERE id = ?", createdAt, id);
        return id;
    }
}
