package basakan.fryday.repository.todo;

import basakan.fryday.domain.todo.TodoAlarm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TodoAlarmRepository extends JpaRepository<TodoAlarm, Long> {

    Optional<TodoAlarm> findByTodoId(Long todoId);

    /**
     * 발송 대기 중인 알림 조회
     * - 푸시 알림 동의한 사용자만 조회
     * - ACTIVE 상태 계정만 조회 (BLOCKED, WITHDRAWN 제외)
     * - 삭제되지 않은 Todo만 조회
     */
    @Query("SELECT ta FROM TodoAlarm ta " +
            "JOIN FETCH ta.user u " +
            "JOIN FETCH ta.todo t " +
            "WHERE ta.status = :status " +
            "AND ta.notifyAt <= :notifyAt " +
            "AND u.accountStatus = 'ACTIVE' " +
            "AND t.deletedAt IS NULL")
    List<TodoAlarm> findAllByStatusAndNotifyAtBeforeOrEqual(
            @Param("status") TodoAlarm.AlarmStatus status,
            @Param("notifyAt") LocalDateTime notifyAt
    );

    void deleteByTodoId(Long todoId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM TodoAlarm ta WHERE ta.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
