package basakan.fryday.repository.todo;

import basakan.fryday.domain.todo.Recurrence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface RecurrenceRepository extends JpaRepository<Recurrence, Long> {

    @Query("SELECT r FROM Recurrence r WHERE r.endDate IS NULL AND r.lastGeneratedDate < :thresholdDate")
    List<Recurrence> findRecurrencesToExtend(@Param("thresholdDate") LocalDate thresholdDate);
}
