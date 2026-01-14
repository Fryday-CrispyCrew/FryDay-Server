package basakan.fryday.repository.todo;

import basakan.fryday.domain.todo.RecurrenceException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface RecurrenceExceptionRepository extends JpaRepository<RecurrenceException, Long> {

    Optional<RecurrenceException> findByRecurrenceIdAndOccurrenceDate(Long recurrenceId, LocalDate occurrenceDate);

    @Query("SELECT re FROM RecurrenceException re WHERE re.recurrenceId = :recurrenceId")
    List<RecurrenceException> findByRecurrenceId(@Param("recurrenceId") Long recurrenceId);
}