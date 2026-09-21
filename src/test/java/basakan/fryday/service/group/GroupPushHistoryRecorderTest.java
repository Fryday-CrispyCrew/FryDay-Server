package basakan.fryday.service.group;

import basakan.fryday.common.config.JpaConfig;
import basakan.fryday.domain.group.GroupPushType;
import basakan.fryday.repository.group.GroupPushHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:group_push_history_recorder_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        }
)
@Import({JpaConfig.class, GroupPushHistoryRecorder.class})
@DisplayName("그룹 푸시 발송 기록")
class GroupPushHistoryRecorderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    @Autowired private GroupPushHistoryRecorder recorder;
    @Autowired private GroupPushHistoryRepository groupPushHistoryRepository;

    @BeforeEach
    void setUp() {
        groupPushHistoryRepository.deleteAll();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("같은 날 같은 알림을 두 번 기록하면 두 번째는 제약 위반으로 실패하고 첫 기록은 남는다")
    void duplicateRecordFails() {
        // given
        recorder.record(1L, 2L, TODAY, GroupPushType.GROUP_FRYING_STARTED);

        // when & then
        assertThatThrownBy(() -> recorder.record(1L, 2L, TODAY, GroupPushType.GROUP_FRYING_STARTED))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(groupPushHistoryRepository.findAllByUserIdAndPushDate(2L, TODAY)).hasSize(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("종류, 그룹, 날짜 중 하나라도 다르면 따로 기록된다")
    void differentKeysAreRecordedSeparately() {
        // when
        recorder.record(1L, 2L, TODAY, GroupPushType.GROUP_FRYING_STARTED);
        recorder.record(1L, 2L, TODAY, GroupPushType.GROUP_FRYING_FINISHED);
        recorder.record(3L, 2L, TODAY, GroupPushType.GROUP_FRYING_STARTED);
        recorder.record(1L, 2L, TODAY.plusDays(1), GroupPushType.GROUP_FRYING_STARTED);

        // then
        assertThat(groupPushHistoryRepository.count()).isEqualTo(4);
    }
}
