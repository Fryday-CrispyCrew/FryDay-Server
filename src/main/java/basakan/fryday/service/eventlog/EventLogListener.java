package basakan.fryday.service.eventlog;

import basakan.fryday.domain.eventlog.EventLog;
import basakan.fryday.repository.eventlog.EventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventLogListener {

    private final EventLogRepository eventLogRepository;

    @Async("eventLogExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserEvent(UserEvent event) {
        try {
            eventLogRepository.save(EventLog.builder()
                    .userId(event.userId())
                    .eventType(event.type())
                    .targetId(event.targetId())
                    .properties(event.properties())
                    .occurredAt(event.occurredAt())
                    .build());
        } catch (RuntimeException e) {
            // 이벤트 로그는 부가 기능이라 저장에 실패해도 유저 요청에는 영향을 주지 않는다
            log.warn("이벤트 로그 저장 실패: userId={}, type={}, targetId={}",
                    event.userId(), event.type(), event.targetId(), e);
        }
    }
}
