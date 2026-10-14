package basakan.fryday.repository.group;

import basakan.fryday.domain.group.GroupPushHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface GroupPushHistoryRepository extends JpaRepository<GroupPushHistory, Long> {

    List<GroupPushHistory> findAllByUserIdAndPushDate(Long userId, LocalDate pushDate);

    @Modifying
    @Query("DELETE FROM GroupPushHistory h WHERE h.pushDate < :date")
    int deleteAllByPushDateBefore(@Param("date") LocalDate date);
}
