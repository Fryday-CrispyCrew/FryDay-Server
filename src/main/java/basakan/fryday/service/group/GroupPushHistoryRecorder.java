package basakan.fryday.service.group;

import basakan.fryday.domain.group.GroupPushHistory;
import basakan.fryday.domain.group.GroupPushType;
import basakan.fryday.repository.group.GroupPushHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 이미 보낸 알림이면 unique 제약 위반으로 실패한다.
 * 이 트랜잭션은 그때 롤백 전용이 되므로, 위반은 반드시 호출하는 쪽에서 잡아야 한다.
 */
@Service
@RequiredArgsConstructor
public class GroupPushHistoryRecorder {

    private final GroupPushHistoryRepository groupPushHistoryRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long groupId, Long userId, LocalDate pushDate, GroupPushType type) {
        groupPushHistoryRepository.saveAndFlush(GroupPushHistory.builder()
                .groupId(groupId)
                .userId(userId)
                .pushDate(pushDate)
                .type(type)
                .build());
    }
}
