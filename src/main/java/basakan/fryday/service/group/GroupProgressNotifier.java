package basakan.fryday.service.group;

import basakan.fryday.domain.group.FryGroup;
import basakan.fryday.domain.group.GroupPushHistory;
import basakan.fryday.domain.group.GroupPushType;
import basakan.fryday.domain.user.User;
import basakan.fryday.repository.auth.UserJpaRepository;
import basakan.fryday.repository.group.FryGroupRepository;
import basakan.fryday.repository.group.GroupMemberRepository;
import basakan.fryday.repository.group.GroupPublicCategoryRepository;
import basakan.fryday.repository.group.GroupPushHistoryRepository;
import basakan.fryday.service.group.dto.GroupProgressDto;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static basakan.fryday.domain.group.GroupPushType.GROUP_FRYING_FINISHED;
import static basakan.fryday.domain.group.GroupPushType.GROUP_FRYING_STARTED;

@Service
@RequiredArgsConstructor
public class GroupProgressNotifier {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final String UNKNOWN_NICKNAME = "그룹원";

    private final GroupPublicCategoryRepository groupPublicCategoryRepository;
    private final GroupPushHistoryRepository groupPushHistoryRepository;
    private final GroupPushHistoryRecorder groupPushHistoryRecorder;
    private final GroupMemberRepository groupMemberRepository;
    private final FryGroupRepository fryGroupRepository;
    private final UserJpaRepository userJpaRepository;
    private final GroupPushSender groupPushSender;

    public void notifyProgress(Long userId) {
        LocalDate today = LocalDate.now(KOREA_ZONE);
        List<GroupProgressDto> progresses = groupPublicCategoryRepository.findProgressByUserAndDate(userId, today);
        if (progresses.isEmpty()) {
            return;
        }

        Map<Long, Set<GroupPushType>> sentTypesByGroupId =
                groupPushHistoryRepository.findAllByUserIdAndPushDate(userId, today).stream()
                        .collect(Collectors.groupingBy(GroupPushHistory::getGroupId,
                                Collectors.mapping(GroupPushHistory::getType, Collectors.toSet())));

        Map<Long, GroupPushType> toSend = new HashMap<>();
        for (GroupProgressDto progress : progresses) {
            GroupPushType type = decide(progress, sentTypesByGroupId.getOrDefault(progress.getGroupId(), Set.of()));
            if (type != null) {
                toSend.put(progress.getGroupId(), type);
            }
        }
        if (toSend.isEmpty()) {
            return;
        }

        String nickname = userJpaRepository.findById(userId)
                .map(User::getNickname)
                .orElse(UNKNOWN_NICKNAME);
        Map<Long, FryGroup> groupsById = fryGroupRepository.findAllById(toSend.keySet()).stream()
                .collect(Collectors.toMap(FryGroup::getId, Function.identity()));

        toSend.forEach((groupId, type) -> {
            FryGroup group = groupsById.get(groupId);
            if (group == null || !record(groupId, userId, today, type)) {
                return;
            }
            groupPushSender.send(
                    groupMemberRepository.findNotifiableUserIds(groupId, userId),
                    group.getName(),
                    message(nickname, type),
                    Map.of("type", type.name(), "groupId", String.valueOf(groupId)));
        });
    }

    static GroupPushType decide(GroupProgressDto progress, Set<GroupPushType> sentToday) {
        if (progress.isAllCompleted()) {
            return sentToday.contains(GROUP_FRYING_FINISHED) ? null : GROUP_FRYING_FINISHED;
        }
        // 영업종료가 이미 나갔다면 그날의 첫 완료는 지나갔으므로 튀기기 시작은 보내지 않는다
        boolean firstCompletionPending = !sentToday.contains(GROUP_FRYING_STARTED)
                && !sentToday.contains(GROUP_FRYING_FINISHED);
        return progress.getCompletedCount() > 0 && firstCompletionPending ? GROUP_FRYING_STARTED : null;
    }

    private boolean record(Long groupId, Long userId, LocalDate today, GroupPushType type) {
        try {
            groupPushHistoryRecorder.record(groupId, userId, today, type);
            return true;
        } catch (DataIntegrityViolationException alreadySent) {
            return false;
        }
    }

    private static String message(String nickname, GroupPushType type) {
        return type == GROUP_FRYING_FINISHED
                ? nickname + "님이 튀김을 다 튀겼습니다!"
                : nickname + "님이 튀김을 튀기기 시작했어요!";
    }
}
