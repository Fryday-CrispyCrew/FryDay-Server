package basakan.fryday.service.admin;

import basakan.fryday.domain.eventlog.EventType;
import basakan.fryday.repository.admin.AdminMetricsRepository.GroupEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("그룹 소속 재생")
class GroupMembershipTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);
    private static final LocalDateTime END = DAY.plusDays(1).atStartOfDay();

    @Test
    @DisplayName("그룹원이 2명 이상인 그룹에 속한 사람만 그룹 사용자다")
    void onlySharedGroups() {
        // given
        GroupMembership membership = new GroupMembership(List.of(
                event(1L, EventType.GROUP_CREATED, 10L, 9),
                event(2L, EventType.GROUP_JOINED, 10L, 10),
                event(3L, EventType.GROUP_CREATED, 20L, 11)));

        // when & then
        assertThat(membership.sharedGroupUsersBefore(END)).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("나간 사람은 빠지고, 남은 사람이 혼자가 되면 그룹 사용자가 아니다")
    void left() {
        // given
        GroupMembership membership = new GroupMembership(List.of(
                event(1L, EventType.GROUP_CREATED, 10L, 9),
                event(2L, EventType.GROUP_JOINED, 10L, 10),
                event(2L, EventType.GROUP_LEFT, 10L, 11)));

        // when & then
        assertThat(membership.sharedGroupUsersBefore(END)).isEmpty();
    }

    @Test
    @DisplayName("해체하면 그룹원 전원이 빠진다")
    void disbanded() {
        // given
        GroupMembership membership = new GroupMembership(List.of(
                event(1L, EventType.GROUP_CREATED, 10L, 9),
                event(2L, EventType.GROUP_JOINED, 10L, 10),
                event(3L, EventType.GROUP_JOINED, 10L, 10),
                event(1L, EventType.GROUP_DISBANDED, 10L, 11)));

        // when & then
        assertThat(membership.sharedGroupUsersBefore(END)).isEmpty();
    }

    @Test
    @DisplayName("회원 탈퇴하면 그룹원으로 있던 모든 그룹에서 빠진다")
    void withdrew() {
        // given
        GroupMembership membership = new GroupMembership(List.of(
                event(1L, EventType.GROUP_CREATED, 10L, 9),
                event(2L, EventType.GROUP_CREATED, 20L, 9),
                event(3L, EventType.GROUP_JOINED, 10L, 10),
                event(3L, EventType.GROUP_JOINED, 20L, 10),
                event(4L, EventType.GROUP_JOINED, 20L, 10),
                event(3L, EventType.USER_WITHDREW, null, 11)));

        // when & then
        assertThat(membership.sharedGroupUsersBefore(END)).containsExactlyInAnyOrder(2L, 4L);
    }

    @Test
    @DisplayName("기준 시각 이후의 이벤트는 반영하지 않는다")
    void beforeEnd() {
        // given
        GroupMembership membership = new GroupMembership(List.of(
                event(1L, EventType.GROUP_CREATED, 10L, 9),
                new GroupEvent(2L, EventType.GROUP_JOINED, 10L, END)));

        // when & then
        assertThat(membership.sharedGroupUsersBefore(END)).isEmpty();
        assertThat(membership.sharedGroupUsersBefore(END.plusSeconds(1))).containsExactlyInAnyOrder(1L, 2L);
    }

    private GroupEvent event(Long userId, EventType type, Long groupId, int hour) {
        return new GroupEvent(userId, type, groupId, DAY.atTime(hour, 0));
    }
}
