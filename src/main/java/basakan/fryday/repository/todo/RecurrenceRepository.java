package basakan.fryday.repository.todo;

import basakan.fryday.domain.todo.EndType;
import basakan.fryday.domain.todo.Recurrence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface RecurrenceRepository extends JpaRepository<Recurrence, Long> {

    @Query("SELECT r FROM Recurrence r WHERE r.endType = :none AND r.lastGeneratedDate < :thresholdDate AND r.isDeleted = false")
    List<Recurrence> findRecurrencesToExtend(@Param("none") EndType none, @Param("thresholdDate") LocalDate thresholdDate);

    @Query("SELECT r FROM Recurrence r WHERE r.userId = :userId " +
           "AND r.isDeleted = false " +
           "AND (r.endType = 'NONE' OR r.endDate >= :date) " +
           "AND r.startDate <= :date")
    List<Recurrence> findByUserIdAndDateRange(@Param("userId") Long userId, @Param("date") LocalDate date);

    void deleteAllByUserId(Long userId);
}
