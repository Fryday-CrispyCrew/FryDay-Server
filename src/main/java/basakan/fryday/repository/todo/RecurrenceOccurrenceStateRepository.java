package basakan.fryday.repository.todo;

import basakan.fryday.domain.todo.RecurrenceOccurrenceState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public interface RecurrenceOccurrenceStateRepository extends JpaRepository<RecurrenceOccurrenceState, Long> {

    Optional<RecurrenceOccurrenceState> findByRecurrenceIdAndOccurrenceDate(Long recurrenceId, LocalDate occurrenceDate);

    @Query("SELECT ros FROM RecurrenceOccurrenceState ros WHERE ros.recurrenceId = :recurrenceId")
    List<RecurrenceOccurrenceState> findByRecurrenceId(@Param("recurrenceId") Long recurrenceId);

    default Map<LocalDate, RecurrenceOccurrenceState.Status> findStatusMapByRecurrenceId(Long recurrenceId) {
        List<RecurrenceOccurrenceState> states = findByRecurrenceId(recurrenceId);
        return states.stream()
                .collect(Collectors.toMap(
                        RecurrenceOccurrenceState::getOccurrenceDate,
                        RecurrenceOccurrenceState::getStatus
                ));
    }
}