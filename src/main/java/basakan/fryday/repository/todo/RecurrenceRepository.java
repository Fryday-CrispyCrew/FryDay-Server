package basakan.fryday.repository.todo;

import basakan.fryday.domain.todo.Recurrence;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurrenceRepository extends JpaRepository<Recurrence, Long> {
}
