package basakan.fryday.service.group;

import basakan.fryday.repository.group.GroupMemberRepository;
import basakan.fryday.service.group.event.GroupProgressChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.anyLong;

@ExtendWith(MockitoExtension.class)
@DisplayName("그룹 SSE 브로드캐스트 리스너")
class GroupSseBroadcastListenerTest {

    private static final Long USER_ID = 1L;

    @Mock private GroupSseService groupSseService;
    @Mock private GroupMemberRepository groupMemberRepository;

    @InjectMocks private GroupSseBroadcastListener listener;

    @Test
    @DisplayName("연결이 하나도 없으면 그룹 조회를 하지 않는다")
    void skipsQueryWhenNoConnections() {
        // given
        given(groupSseService.isEmpty()).willReturn(true);

        // when
        listener.onGroupProgressChanged(new GroupProgressChangedEvent(USER_ID));

        // then
        then(groupMemberRepository).should(never()).findGroupIdsByUserId(anyLong());
        then(groupSseService).should(never()).broadcast(anyLong(), anyLong());
    }

    @Test
    @DisplayName("변경한 유저가 속한 모든 그룹에 브로드캐스트한다")
    void broadcastsToAllGroupsOfUser() {
        // given
        given(groupSseService.isEmpty()).willReturn(false);
        given(groupMemberRepository.findGroupIdsByUserId(USER_ID)).willReturn(List.of(10L, 20L));

        // when
        listener.onGroupProgressChanged(new GroupProgressChangedEvent(USER_ID));

        // then
        then(groupSseService).should().broadcast(10L, USER_ID);
        then(groupSseService).should().broadcast(20L, USER_ID);
    }
}
