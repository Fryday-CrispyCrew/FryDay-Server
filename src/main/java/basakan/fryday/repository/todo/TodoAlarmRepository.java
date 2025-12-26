package basakan.fryday.repository.todo;

import basakan.fryday.domain.todo.TodoAlarm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TodoAlarmRepository extends JpaRepository<TodoAlarm, Long> {

    Optional<TodoAlarm> findByTodoId(Long todoId);

    @Query("SELECT ta FROM TodoAlarm ta " +
            "JOIN FETCH ta.user " +
            "JOIN FETCH ta.todo " +
            "WHERE ta.status = :status AND ta.notifyAt < :notifyAt")
    List<TodoAlarm> findAllByStatusAndNotifyAtBefore(
            @Param("status") TodoAlarm.AlarmStatus status,
            @Param("notifyAt") LocalDateTime notifyAt
    );

    void deleteByTodoId(Long todoId);
}
