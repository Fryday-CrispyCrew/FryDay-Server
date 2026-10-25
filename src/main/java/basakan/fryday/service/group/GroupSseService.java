package basakan.fryday.service.group;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.repository.group.FryGroupRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GroupSseService {

    private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;
    private static final long HEARTBEAT_INTERVAL_MILLIS = 25 * 1000L;

    private final FryGroupRepository fryGroupRepository;
    private final Map<Long, Map<String, SseEmitter>> emittersByGroup = new ConcurrentHashMap<>();
    private final Map<DisconnectReason, Counter> disconnectCounters = new EnumMap<>(DisconnectReason.class);

    // http.server.requests는 SSE 연결 유지 시간 전체를 응답 시간으로 기록하므로, 연결 상태는 이 지표로 본다.
    // 종료 사유 Counter를 미리 등록해 0부터 노출해야 첫 증가분이 increase()에서 빠지지 않는다.
    public GroupSseService(FryGroupRepository fryGroupRepository, MeterRegistry meterRegistry) {
        this.fryGroupRepository = fryGroupRepository;
        Gauge.builder("group.sse.connections", this, GroupSseService::connectionCount)
                .description("열려 있는 그룹 SSE 연결 수")
                .register(meterRegistry);
        for (DisconnectReason reason : DisconnectReason.values()) {
            disconnectCounters.put(reason, Counter.builder("group.sse.disconnects")
                    .description("그룹 SSE 연결 종료 횟수")
                    .tag("reason", reason.tag)
                    .register(meterRegistry));
        }
    }

    public SseEmitter connect(Long groupId, Long userId) {
        fryGroupRepository.findByIdAndMemberUserId(groupId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        String connectionId = UUID.randomUUID().toString();
        emitter.onCompletion(() -> remove(groupId, connectionId, DisconnectReason.COMPLETION));
        emitter.onTimeout(() -> remove(groupId, connectionId, DisconnectReason.TIMEOUT));
        emitter.onError(e -> remove(groupId, connectionId, DisconnectReason.ERROR));
        emittersByGroup.computeIfAbsent(groupId, id -> new ConcurrentHashMap<>()).put(connectionId, emitter);

        send(groupId, connectionId, emitter, SseEmitter.event().name("connected").data("ok"));
        return emitter;
    }

    public void broadcast(Long groupId, Long memberUserId) {
        Map<String, SseEmitter> emitters = emittersByGroup.get(groupId);
        if (emitters == null) {
            return;
        }
        emitters.forEach((connectionId, emitter) -> send(groupId, connectionId, emitter,
                SseEmitter.event().name("group-progress").data(Map.of("memberUserId", memberUserId))));
    }

    public boolean isEmpty() {
        return emittersByGroup.values().stream().allMatch(Map::isEmpty);
    }

    /** 유휴 연결이 중간 장비에서 끊기지 않게 하고, 끊긴 지 모르는 연결을 걷어낸다. */
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MILLIS)
    public void heartbeat() {
        emittersByGroup.forEach((groupId, emitters) ->
                emitters.forEach((connectionId, emitter) ->
                        send(groupId, connectionId, emitter, SseEmitter.event().comment("ping"))));
    }

    private void send(Long groupId, String connectionId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            remove(groupId, connectionId, DisconnectReason.SEND_FAILURE);
        }
    }

    private int connectionCount() {
        return emittersByGroup.values().stream().mapToInt(Map::size).sum();
    }

    // 빈 그룹 맵은 정리하지 않는다. connect의 put과 경합해 새 연결을 잃을 수 있어서다.
    // timeout·error 뒤에는 onCompletion이 한 번 더 불리므로, 실제로 제거한 첫 호출의 사유만 센다.
    private void remove(Long groupId, String connectionId, DisconnectReason reason) {
        Map<String, SseEmitter> emitters = emittersByGroup.get(groupId);
        if (emitters != null && emitters.remove(connectionId) != null) {
            disconnectCounters.get(reason).increment();
        }
    }

    private enum DisconnectReason {
        COMPLETION("completion"),
        TIMEOUT("timeout"),
        ERROR("error"),
        SEND_FAILURE("send_failure");

        private final String tag;

        DisconnectReason(String tag) {
            this.tag = tag;
        }
    }
}
