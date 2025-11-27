package basakan.fryday.repository;

import basakan.fryday.domain.Todo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TodoRepository extends JpaRepository<Todo, Long> {
    List<Todo> findAllByCategory_UserIdAndDateAndDeletedAtIsNullOrderByDisplayOrderAsc(Long userId, LocalDate date);

    @Query("SELECT MAX(t.displayOrder) FROM Todo t JOIN t.category c WHERE c.userId = :userId AND t.date = :date AND t.deletedAt IS NULL")
    Long findMaxDisplayOrder(@Param("userId") Long userId, @Param("date") LocalDate date);
}
