package basakan.fryday.repository.group;

import basakan.fryday.domain.group.FryGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FryGroupRepository extends JpaRepository<FryGroup, Long> {

    boolean existsByInviteCode(String inviteCode);

    /** 요청자가 그룹원인 경우에만 그룹을 반환한다. 비그룹원에게는 그룹의 존재 자체를 감춘다. */
    @Query("SELECT g FROM FryGroup g " +
            "WHERE g.id = :groupId " +
            "  AND EXISTS (SELECT 1 FROM GroupMember gm WHERE gm.groupId = g.id AND gm.userId = :userId)")
    Optional<FryGroup> findByIdAndMemberUserId(@Param("groupId") Long groupId, @Param("userId") Long userId);

    List<FryGroup> findAllByOwnerId(Long ownerId);

    /**
     * 자식 행을 벌크로 지운 뒤에 호출된다. 그 과정에서 그룹 엔티티가 영속성 컨텍스트에서 분리되므로
     * {@code delete(entity)} 나 {@code deleteById} 를 쓰면 재조회 SELECT 가 한 번 더 붙는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM FryGroup g WHERE g.id = :groupId")
    void deleteGroupById(@Param("groupId") Long groupId);

}
