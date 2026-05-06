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

    @Query("SELECT DISTINCT c.userId FROM Todo t JOIN t.category c " +
            "WHERE t.date = :date AND t.status = :status AND t.deletedAt IS NULL")
    List<Long> findUserIdsWithTodosByDateAndStatus(@Param("date") LocalDate date, @Param("status") Todo.Status status);

    @Query("SELECT DISTINCT c.userId FROM Todo t JOIN t.category c " +
            "WHERE t.date = :date AND t.deletedAt IS NULL")
    List<Long> findUserIdsWithTodosByDate(@Param("date") LocalDate date);

    @Query("SELECT t FROM Todo t WHERE t.recurrenceId = :recurrenceId AND t.deletedAt IS NULL")
    List<Todo> findAllByRecurrenceId(@Param("recurrenceId") Long recurrenceId);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN TRUE ELSE FALSE END FROM Todo t WHERE t.recurrenceId = :recurrenceId AND t.date = :date")
    boolean existsByRecurrenceIdAndDate(@Param("recurrenceId") Long recurrenceId, @Param("date") LocalDate date);

    @Query("SELECT t FROM Todo t WHERE t.recurrenceId = :recurrenceId AND t.date = :date")
    java.util.Optional<Todo> findByRecurrenceIdAndDate(@Param("recurrenceId") Long recurrenceId, @Param("date") LocalDate date);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Todo t SET t.deletedAt = :now WHERE t.recurrenceId = :recurrenceId AND t.date >= :fromDate AND t.deletedAt IS NULL")
    int bulkSoftDeleteByRecurrenceIdAndDateGte(@Param("recurrenceId") Long recurrenceId, @Param("fromDate") LocalDate fromDate, @Param("now") LocalDate now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Todo t SET t.deletedAt = :now WHERE t.recurrenceId = :recurrenceId AND t.deletedAt IS NULL")
    int bulkSoftDeleteByRecurrenceId(@Param("recurrenceId") Long recurrenceId, @Param("now") LocalDate now);

    @Query("SELECT new basakan.fryday.service.report.dto.CategoryReportDto(" +
           "c.id, " +
           "c.name, " +
           "c.color, " +
           "CAST(COUNT(t.id) AS int), " +
           "CAST(SUM(CASE WHEN t.status = 'COMPLETED' THEN 1 ELSE 0 END) AS int), " +
           "CAST(SUM(CASE WHEN t.status != 'COMPLETED' THEN 1 ELSE 0 END) AS int)) " +
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

    @Query("SELECT new basakan.fryday.service.report.dto.CategoryReportDto(" +
           "c.id, " +
           "c.name, " +
           "c.color, " +
           "CAST(COUNT(t.id) AS int), " +
           "CAST(SUM(CASE WHEN t.status = 'COMPLETED' THEN 1 ELSE 0 END) AS int), " +
           "CAST(SUM(CASE WHEN t.status != 'COMPLETED' AND t.date < :endDate THEN 1 ELSE 0 END) AS int)) " +
           "FROM Todo t " +
           "JOIN t.category c " +
           "WHERE c.userId = :userId " +
           "  AND YEAR(t.date) = :year " +
           "  AND MONTH(t.date) = :month " +
           "  AND t.date <= :endDate " +
           "  AND t.deletedAt IS NULL " +
           "GROUP BY c.id, c.name, c.color")
    List<CategoryReportDto> findMonthlyReportByCategoryUntilDate(
        @Param("userId") Long userId,
        @Param("year") int year,
        @Param("month") int month,
        @Param("endDate") LocalDate endDate
    );

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Todo t SET t.description = :description WHERE t.id = :todoId AND t.deletedAt IS NULL AND t.category.userId = :userId")
    int updateDescriptionByIdAndUserId(@Param("todoId") Long todoId, @Param("userId") Long userId, @Param("description") String description);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Todo t SET t.memo = :memo WHERE t.id = :todoId AND t.deletedAt IS NULL AND t.category.userId = :userId")
    int updateMemoByIdAndUserId(@Param("todoId") Long todoId, @Param("userId") Long userId, @Param("memo") String memo);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Todo t SET t.date = :date WHERE t.id = :todoId AND t.deletedAt IS NULL AND t.recurrenceId IS NULL AND t.category.userId = :userId")
    int updateDateByIdAndUserIdForNonRecurring(@Param("todoId") Long todoId, @Param("userId") Long userId, @Param("date") LocalDate date);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Todo t SET t.category = (SELECT c FROM Category c WHERE c.id = :categoryId AND c.userId = :userId) WHERE t.id = :todoId AND t.deletedAt IS NULL AND t.category.userId = :userId")
    int updateCategoryByIdAndUserId(@Param("todoId") Long todoId, @Param("userId") Long userId, @Param("categoryId") Long categoryId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Todo t SET t.recurrenceId = :recurrenceId WHERE t.id = :todoId AND t.deletedAt IS NULL AND t.category.userId = :userId")
    int updateRecurrenceIdByIdAndUserId(@Param("todoId") Long todoId, @Param("userId") Long userId, @Param("recurrenceId") Long recurrenceId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Todo t SET t.description = :description WHERE t.recurrenceId = :recurrenceId AND t.isOverridden = false AND t.deletedAt IS NULL")
    int bulkUpdateDescriptionByRecurrenceId(@Param("recurrenceId") Long recurrenceId, @Param("description") String description);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Todo t SET t.memo = :memo WHERE t.recurrenceId = :recurrenceId AND t.isOverridden = false AND t.deletedAt IS NULL")
    int bulkUpdateMemoByRecurrenceId(@Param("recurrenceId") Long recurrenceId, @Param("memo") String memo);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Todo t WHERE t.category.id IN " +
            "(SELECT c.id FROM Category c WHERE c.userId = :userId)")
    void deleteAllByUserId(@Param("userId") Long userId);

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

    @Query("SELECT CAST(COUNT(DISTINCT DATE(t.date)) AS int) " +
           "FROM Todo t " +
           "JOIN t.category c " +
           "WHERE c.userId = :userId " +
           "  AND YEAR(t.date) = :year " +
           "  AND MONTH(t.date) = :month " +
           "  AND t.date <= :endDate " +
           "  AND t.deletedAt IS NULL")
    int countAttendanceDaysUntilDate(
        @Param("userId") Long userId,
        @Param("year") int year,
        @Param("month") int month,
        @Param("endDate") LocalDate endDate
    );
}
