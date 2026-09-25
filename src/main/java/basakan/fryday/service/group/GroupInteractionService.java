package basakan.fryday.service.group;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.group.request.GroupInteractionRequest;
import basakan.fryday.domain.group.FryGroup;
import basakan.fryday.domain.group.GroupInteraction;
import basakan.fryday.domain.group.GroupInteractionType;
import basakan.fryday.domain.group.GroupMember;
import basakan.fryday.domain.group.GroupMemberStatus;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.auth.UserJpaRepository;
import basakan.fryday.repository.group.FryGroupRepository;
import basakan.fryday.repository.group.GroupInteractionRepository;
import basakan.fryday.repository.group.GroupMemberRepository;
import basakan.fryday.repository.group.GroupPublicCategoryRepository;
import basakan.fryday.service.group.event.GroupInteractionEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class GroupInteractionService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final FryGroupRepository fryGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupPublicCategoryRepository groupPublicCategoryRepository;
    private final GroupInteractionRepository groupInteractionRepository;
    private final UserJpaRepository userJpaRepository;
    private final GroupInteractionCooldown groupInteractionCooldown;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void interact(Long groupId, Long targetUserId, GroupInteractionRequest request, Long senderId) {
        FryGroup group = fryGroupRepository.findByIdAndMemberUserId(groupId, senderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
        if (targetUserId.equals(senderId)) {
            throw new BusinessException(ErrorCode.INTERACTION_SELF_NOT_ALLOWED);
        }
        GroupMember target = groupMemberRepository.findByGroupIdAndUserId(groupId, targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        GroupInteractionType type = request.getType();
        if (currentStatusOf(groupId, targetUserId).getAvailableInteraction() != type) {
            throw new BusinessException(ErrorCode.INTERACTION_STATUS_MISMATCH);
        }
        // 이후 트랜잭션이 롤백되어도 쿨다운 키는 남는다. 알림 없이 30초 잠기는 정도라 감수한다
        if (!groupInteractionCooldown.tryAcquire(groupId, senderId, targetUserId, type)) {
            throw new BusinessException(ErrorCode.INTERACTION_COOLDOWN);
        }

        groupInteractionRepository.save(GroupInteraction.builder()
                .groupId(groupId)
                .senderId(senderId)
                .targetId(targetUserId)
                .type(type)
                .build());

        if (target.isNotificationEnabled()) {
            eventPublisher.publishEvent(new GroupInteractionEvent(
                    groupId,
                    group.getName(),
                    targetUserId,
                    userJpaRepository.findById(targetUserId).map(User::getNickname).orElse(null),
                    type));
        }
    }

    private GroupMemberStatus currentStatusOf(Long groupId, Long userId) {
        return groupPublicCategoryRepository.findTodoCountsByGroupAndDate(groupId, LocalDate.now(KOREA_ZONE)).stream()
                .filter(count -> count.getUserId().equals(userId))
                .findFirst()
                .map(count -> GroupMemberStatus.of(count.getTotalCount(), count.getCompletedCount()))
                .orElse(GroupMemberStatus.BEFORE_OPEN);
    }
}
