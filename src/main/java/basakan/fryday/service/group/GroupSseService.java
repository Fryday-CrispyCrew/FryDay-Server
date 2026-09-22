package basakan.fryday.service.group;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.repository.group.FryGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class GroupSseService {

    private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;
    private static final long HEARTBEAT_INTERVAL_MILLIS = 25 * 1000L;

    private final FryGroupRepository fryGroupRepository;
    private final Map<Long, Map<String, SseEmitter>> emittersByGroup = new ConcurrentHashMap<>();

    public SseEmitter connect(Long groupId, Long userId) {
        fryGroupRepository.findByIdAndMemberUserId(groupId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        String connectionId = UUID.randomUUID().toString();
        emitter.onCompletion(() -> remove(groupId, connectionId));
        emitter.onTimeout(() -> remove(groupId, connectionId));
        emitter.onError(e -> remove(groupId, connectionId));
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
            remove(groupId, connectionId);
        }
    }

    // 빈 그룹 맵은 정리하지 않는다. connect의 put과 경합해 새 연결을 잃을 수 있어서다.
    private void remove(Long groupId, String connectionId) {
        Map<String, SseEmitter> emitters = emittersByGroup.get(groupId);
        if (emitters != null) {
            emitters.remove(connectionId);
        }
    }
}
