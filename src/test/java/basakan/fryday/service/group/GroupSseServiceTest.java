package basakan.fryday.service.group;

import basakan.fryday.domain.group.FryGroup;
import basakan.fryday.repository.group.FryGroupRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("그룹 SSE 지표")
class GroupSseServiceTest {

    private static final Long GROUP_ID = 1L;
    private static final Long USER_ID = 1L;

    private final FryGroupRepository fryGroupRepository = mock(FryGroupRepository.class);
    private SimpleMeterRegistry meterRegistry;
    private GroupSseService groupSseService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        groupSseService = new GroupSseService(fryGroupRepository, meterRegistry);
        given(fryGroupRepository.findByIdAndMemberUserId(GROUP_ID, USER_ID))
                .willReturn(Optional.of(mock(FryGroup.class)));
    }

    @Test
    @DisplayName("종료 사유별 Counter는 연결 전부터 0으로 노출된다")
    void disconnectCountersStartAtZero() {
        for (String reason : new String[]{"completion", "timeout", "error", "send_failure"}) {
            assertThat(disconnects(reason)).isZero();
        }
    }

    @Test
    @DisplayName("연결할 때마다 열린 연결 수가 늘어난다")
    void connectionGauge() {
        groupSseService.connect(GROUP_ID, USER_ID);
        groupSseService.connect(GROUP_ID, USER_ID);

        assertThat(connections()).isEqualTo(2);
    }

    @Test
    @DisplayName("전송에 실패한 연결은 제거되고 send_failure로 한 번만 집계된다")
    void sendFailure() {
        SseEmitter emitter = groupSseService.connect(GROUP_ID, USER_ID);
        groupSseService.connect(GROUP_ID, USER_ID);
        emitter.complete();

        groupSseService.heartbeat();
        groupSseService.heartbeat();

        assertThat(connections()).isEqualTo(1);
        assertThat(disconnects("send_failure")).isEqualTo(1);
    }

    private double connections() {
        return meterRegistry.get("group.sse.connections").gauge().value();
    }

    private double disconnects(String reason) {
        return meterRegistry.get("group.sse.disconnects").tag("reason", reason).counter().count();
    }
}
