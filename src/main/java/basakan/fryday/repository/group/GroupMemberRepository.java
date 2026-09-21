package basakan.fryday.repository.group;

import basakan.fryday.domain.group.GroupMember;
import basakan.fryday.service.group.dto.GroupMemberDto;
import basakan.fryday.service.group.dto.GroupSummaryDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    long countByGroupId(Long groupId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM GroupMember gm WHERE gm.groupId = :groupId")
    void deleteAllByGroupId(@Param("groupId") Long groupId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM GroupMember gm WHERE gm.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM GroupMember gm WHERE gm.groupId = :groupId AND gm.userId = :userId")
    void deleteAllByGroupIdAndUserId(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /** 내가 참여 중인 그룹을 최근 가입순으로 조회한다. 인원수는 그룹별 서브쿼리로 한 번에 센다. */
    @Query("SELECT new basakan.fryday.service.group.dto.GroupSummaryDto(" +
            "g.id, g.name, g.ownerId, " +
            "(SELECT COUNT(gm2) FROM GroupMember gm2 WHERE gm2.groupId = g.id)) " +
            "FROM GroupMember gm " +
            "JOIN FryGroup g ON g.id = gm.groupId " +
            "WHERE gm.userId = :userId " +
            "ORDER BY gm.createdAt DESC, gm.id DESC")
    List<GroupSummaryDto> findMyGroups(@Param("userId") Long userId);

    /** 그룹원을 참여 순서대로 조회한다. 탈퇴 대기 중인 계정은 제외한다. */
    @Query("SELECT new basakan.fryday.service.group.dto.GroupMemberDto(gm.userId, u.nickname, gm.createdAt) " +
            "FROM GroupMember gm " +
            "JOIN User u ON u.id = gm.userId AND u.accountStatus = 'ACTIVE' " +
            "WHERE gm.groupId = :groupId " +
            "ORDER BY gm.createdAt ASC, gm.id ASC")
    List<GroupMemberDto> findMembersWithNickname(@Param("groupId") Long groupId);
}
