package basakan.fryday.repository.todo;

import basakan.fryday.domain.todo.Todo;
import basakan.fryday.service.report.dto.CategoryReportDto;
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

    @Query("SELECT t FROM Todo t " +
            "WHERE t.category.userId = :userId AND t.date = :date AND t.deletedAt IS NULL " +
            "ORDER BY t.displayOrder ASC")
    List<Todo> findAllByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Query("SELECT t FROM Todo t " +
            "WHERE t.category.id = :categoryId AND t.date = :date AND t.deletedAt IS NULL " +
            "ORDER BY t.displayOrder ASC")
    List<Todo> findAllByCategoryIdAndDate(@Param("categoryId") Long categoryId, @Param("date") LocalDate date);

    @Modifying
    @Query("UPDATE Todo t SET t.isBurnt = true WHERE t.date = :date AND t.status = :status AND t.deletedAt IS NULL")
    int updateBurntStatusByDate(@Param("date") LocalDate date, @Param("status") Todo.Status status);

    @Query("SELECT COUNT(t) FROM Todo t JOIN t.category c " +
            "WHERE c.userId = :userId AND t.date = :date AND t.status = :status AND t.deletedAt IS NULL")
    long countByUserIdAndDateAndStatus(@Param("userId") Long userId, @Param("date") LocalDate date, @Param("status") Todo.Status status);

    @Query("SELECT COUNT(t) FROM Todo t JOIN t.category c " +
            "WHERE c.userId = :userId AND t.date = :date AND t.deletedAt IS NULL")
    long countByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Query("SELECT DISTINCT c.userId FROM Todo t JOIN t.category c " +
            "WHERE t.date = :date AND t.isBurnt = true AND t.deletedAt IS NULL")
    List<Long> findUserIdsWithBurntTodosByDate(@Param("date") LocalDate date);

    @Query("SELECT t FROM Todo t WHERE t.recurrenceId = :recurrenceId AND t.deletedAt IS NULL")
    List<Todo> findAllByRecurrenceId(@Param("recurrenceId") Long recurrenceId);

    @Query("SELECT new basakan.fryday.service.report.dto.CategoryReportDto(" +
           "c.id, " +
           "c.name, " +
           "c.color, " +
           "CAST(COUNT(t.id) AS int), " +
           "CAST(SUM(CASE WHEN t.status = 'COMPLETED' THEN 1 ELSE 0 END) AS int), " +
           "CAST(SUM(CASE WHEN t.status = 'IN_PROGRESS' OR t.isBurnt = true THEN 1 ELSE 0 END) AS int)) " +
           "FROM Todo t " +
           "JOIN t.category c " +
           "WHERE c.userId = :userId " +
           "  AND YEAR(t.date) = :year " +
           "  AND MONTH(t.date) = :month " +
           "  AND t.deletedAt IS NULL " +
           "GROUP BY c.id, c.name, c.color")
    List<CategoryReportDto> findMonthlyReportByCategory(
        @Param("userId") Long userId,
        @Param("year") int year,
        @Param("month") int month
    );

    @Query("SELECT CAST(COUNT(DISTINCT DATE(t.date)) AS int) " +
           "FROM Todo t " +
           "JOIN t.category c " +
           "WHERE c.userId = :userId " +
           "  AND YEAR(t.date) = :year " +
           "  AND MONTH(t.date) = :month " +
           "  AND t.deletedAt IS NULL")
    int countAttendanceDays(
        @Param("userId") Long userId,
        @Param("year") int year,
        @Param("month") int month
    );
}
