package basakan.fryday.repository.todo;

import basakan.fryday.domain.todo.Todo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TodoRepository extends JpaRepository<Todo, Long> {
    List<Todo> findAllByCategory_UserIdAndDateAndDeletedAtIsNullOrderByDisplayOrderAsc(Long userId, LocalDate date);

    @Query("SELECT MAX(t.displayOrder) FROM Todo t JOIN t.category c WHERE c.userId = :userId AND t.date = :date AND t.deletedAt IS NULL")
    Long findMaxDisplayOrder(@Param("userId") Long userId, @Param("date") LocalDate date);

    // 전체 보기
    @Query("SELECT t FROM Todo t " +
            "WHERE t.category.userId = :userId AND t.date = :date AND t.deletedAt IS NULL " +
            "ORDER BY t.displayOrder ASC")
    List<Todo> findAllByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    // 카테고리별 보기
    @Query("SELECT t FROM Todo t " +
            "WHERE t.category.id = :categoryId AND t.date = :date AND t.deletedAt IS NULL " +
            "ORDER BY t.displayOrder ASC")
    List<Todo> findAllByCategoryIdAndDate(@Param("categoryId") Long categoryId, @Param("date") LocalDate date);

    // 특정 날짜의 미완료 투두를 탄 튀김 상태로 일괄 업데이트
    @Modifying
    @Query("UPDATE Todo t SET t.isBurnt = true WHERE t.date = :date AND t.status = :status AND t.deletedAt IS NULL")
    int updateBurntStatusByDate(@Param("date") LocalDate date, @Param("status") Todo.Status status);
}
