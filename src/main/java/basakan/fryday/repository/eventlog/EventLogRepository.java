package basakan.fryday.repository.eventlog;

import basakan.fryday.domain.eventlog.EventLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventLogRepository extends JpaRepository<EventLog, Long> {
}
