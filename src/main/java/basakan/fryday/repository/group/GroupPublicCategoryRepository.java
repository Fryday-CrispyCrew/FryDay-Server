package basakan.fryday.repository.group;

import basakan.fryday.domain.group.GroupPublicCategory;
import basakan.fryday.service.group.dto.GroupMemberTodoCountDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface GroupPublicCategoryRepository extends JpaRepository<GroupPublicCategory, Long> {

    List<GroupPublicCategory> findAllByGroupIdAndUserId(Long groupId, Long userId);

    long countByGroupIdAndUserId(Long groupId, Long userId);

    /**
     * 공개 카테고리 교체 시 삭제가 삽입보다 먼저 실행되어야 unique 제약에 걸리지 않으므로 벌크 DML 로 즉시 지운다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM GroupPublicCategory gpc WHERE gpc.groupId = :groupId AND gpc.userId = :userId")
    void deleteAllByGroupIdAndUserId(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM GroupPublicCategory gpc WHERE gpc.groupId = :groupId")
    void deleteAllByGroupId(@Param("groupId") Long groupId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM GroupPublicCategory gpc WHERE gpc.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM GroupPublicCategory gpc WHERE gpc.categoryId = :categoryId")
    void deleteAllByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * 그룹원별 특정 날짜 투두 집계. 각자 이 그룹에 공개한 카테고리의 투두만 센다.
     * 투두가 하나도 없는 그룹원은 결과에 포함되지 않으므로, 호출부에서 그룹원 목록 기준으로 0을 채워야 한다.
     */
    @Query("SELECT new basakan.fryday.service.group.dto.GroupMemberTodoCountDto(" +
            "gpc.userId, " +
            "CAST(COUNT(t.id) AS int), " +
            "CAST(SUM(CASE WHEN t.status = 'COMPLETED' THEN 1 ELSE 0 END) AS int)) " +
            "FROM GroupPublicCategory gpc " +
            "JOIN Category c ON c.id = gpc.categoryId AND c.deletedAt IS NULL " +
            "JOIN Todo t ON t.category = c AND t.date = :date AND t.deletedAt IS NULL " +
            "WHERE gpc.groupId = :groupId " +
            "GROUP BY gpc.userId")
    List<GroupMemberTodoCountDto> findTodoCountsByGroupAndDate(@Param("groupId") Long groupId,
                                                               @Param("date") LocalDate date);
}
