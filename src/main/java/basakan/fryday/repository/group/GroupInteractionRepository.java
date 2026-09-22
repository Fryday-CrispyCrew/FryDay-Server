package basakan.fryday.repository.group;

import basakan.fryday.domain.group.GroupInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface GroupInteractionRepository extends JpaRepository<GroupInteraction, Long> {

    @Modifying
    @Query("DELETE FROM GroupInteraction i WHERE i.createdAt < :threshold")
    int deleteAllByCreatedAtBefore(@Param("threshold") LocalDateTime threshold);
}
