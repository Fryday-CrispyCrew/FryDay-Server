package basakan.fryday.service.admin;

import basakan.fryday.repository.admin.AdminMetricsRepository.GroupEvent;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * group_member 는 지금 소속만 남고 나가거나 해체되면 행이 지워진다. 그래서 그룹 이벤트를 순서대로 재생해 지난 시점의 소속을 구한다.
 */
final class GroupMembership {

    // 혼자인 그룹은 다른 사람과 주고받는 그룹 기능을 경험하지 않으므로 그룹 사용자로 보지 않는다
    static final int MIN_GROUP_SIZE = 2;

    private final List<GroupEvent> events;

    /**
     * @param events 발생 순서대로 정렬된 그룹 생성, 참여, 나가기, 해체, 회원 탈퇴 이벤트
     */
    GroupMembership(List<GroupEvent> events) {
        this.events = events;
    }

    Set<Long> sharedGroupUsersBefore(LocalDateTime endExclusive) {
        Map<Long, Set<Long>> membersByGroup = new HashMap<>();
        for (GroupEvent event : events) {
            if (!event.occurredAt().isBefore(endExclusive)) {
                break;
            }
            switch (event.type()) {
                case GROUP_CREATED, GROUP_JOINED ->
                        membersByGroup.computeIfAbsent(event.groupId(), key -> new HashSet<>()).add(event.userId());
                case GROUP_LEFT -> membersByGroup.getOrDefault(event.groupId(), new HashSet<>()).remove(event.userId());
                case GROUP_DISBANDED -> membersByGroup.remove(event.groupId());
                // 그룹장인 그룹은 해체 이벤트가 따로 남고, 여기서는 그룹원으로 있던 그룹에서 빠진다
                case USER_WITHDREW -> membersByGroup.values().forEach(members -> members.remove(event.userId()));
                default -> {
                }
            }
        }
        return membersByGroup.values().stream()
                .filter(members -> members.size() >= MIN_GROUP_SIZE)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }
}
