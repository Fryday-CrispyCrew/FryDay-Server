package basakan.fryday.service.group;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.group.request.GroupCreateRequest;
import basakan.fryday.controller.group.request.GroupNameUpdateRequest;
import basakan.fryday.controller.group.response.GroupCreateResponse;
import basakan.fryday.controller.group.response.GroupNameResponse;
import basakan.fryday.domain.group.FryGroup;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.auth.UserJpaRepository;
import basakan.fryday.repository.group.FryGroupRepository;
import basakan.fryday.repository.group.GroupMemberRepository;
import basakan.fryday.repository.group.GroupPublicCategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("그룹 서비스 - 생성/이름변경/해체")
class GroupServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final Long GROUP_ID = 100L;

    @Mock private FryGroupRepository fryGroupRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private GroupPublicCategoryRepository groupPublicCategoryRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private UserJpaRepository userJpaRepository;
    @Mock private GroupCreator groupCreator;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private GroupService groupService;

    @Test
    @DisplayName("그룹을 만들면 검증을 통과한 이름으로 생성을 위임한다")
    void createGroup() {
        // given
        given(groupCreator.create("바삭한 사람들", OWNER_ID))
                .willReturn(GroupCreateResponse.from(group(OWNER_ID)));

        // when
        GroupCreateResponse response = groupService.createGroup(new GroupCreateRequest("바삭한 사람들"), OWNER_ID);

        // then
        assertThat(response.getGroupId()).isEqualTo(GROUP_ID);
        assertThat(response.getInviteCode()).isEqualTo("FRY123");
        assertThat(response.getMemberCount()).isEqualTo(1);
        assertThat(response.getMaxMemberCount()).isEqualTo(FryGroup.MAX_MEMBER_COUNT);
    }

    @Test
    @DisplayName("이름 양끝 공백은 제거된 뒤 전달된다")
    void createGroupTrimsName() {
        // given
        given(groupCreator.create("바삭한 사람", OWNER_ID))
                .willReturn(GroupCreateResponse.from(group(OWNER_ID)));

        // when
        groupService.createGroup(new GroupCreateRequest("  바삭한 사람  "), OWNER_ID);

        // then
        then(groupCreator).should().create("바삭한 사람", OWNER_ID);
    }

    @Test
    @DisplayName("초대 코드가 동시 요청과 겹치면 새 코드로 다시 만들어 사용자에게는 성공으로 보인다")
    void retriesWhenInviteCodeCollides() {
        // given
        given(groupCreator.create("바삭한 사람들", OWNER_ID))
                .willThrow(new DataIntegrityViolationException("uk_fry_group_invite_code"))
                .willThrow(new DataIntegrityViolationException("uk_fry_group_invite_code"))
                .willReturn(GroupCreateResponse.from(group(OWNER_ID)));

        // when
        GroupCreateResponse response = groupService.createGroup(new GroupCreateRequest("바삭한 사람들"), OWNER_ID);

        // then
        assertThat(response.getInviteCode()).isEqualTo("FRY123");
        then(groupCreator).should(times(3)).create("바삭한 사람들", OWNER_ID);
    }

    @Test
    @DisplayName("재시도를 모두 소진하도록 초대 코드가 겹치면 예외를 던진다")
    void failsWhenInviteCodeKeepsColliding() {
        // given
        given(groupCreator.create("바삭한 사람들", OWNER_ID))
                .willThrow(new DataIntegrityViolationException("uk_fry_group_invite_code"));

        // when & then
        assertThatThrownBy(() -> groupService.createGroup(new GroupCreateRequest("바삭한 사람들"), OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVITE_CODE_GENERATION_FAILED.getMessage());
        then(groupCreator).should(times(5)).create("바삭한 사람들", OWNER_ID);
    }

    @Test
    @DisplayName("공백만 입력하면 그룹을 만들 수 없다")
    void createGroupRejectsBlankName() {
        assertThatThrownBy(() -> groupService.createGroup(new GroupCreateRequest("   "), OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_GROUP_NAME.getMessage());

        then(groupCreator).should(never()).create(any(), anyLong());
    }

    @Test
    @DisplayName("11자 이상 이름으로는 그룹을 만들 수 없다")
    void createGroupRejectsTooLongName() {
        assertThatThrownBy(() -> groupService.createGroup(new GroupCreateRequest("가나다라마바사아자차카"), OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_GROUP_NAME.getMessage());

        then(groupCreator).should(never()).create(any(), anyLong());
    }

    @Test
    @DisplayName("이모지가 들어간 이름으로는 그룹을 만들 수 없다")
    void createGroupRejectsEmojiName() {
        assertThatThrownBy(() -> groupService.createGroup(new GroupCreateRequest("바삭한 🍤"), OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.GROUP_NAME_HAS_EMOJI.getMessage());

        then(groupCreator).should(never()).create(any(), anyLong());
    }

    @Test
    @DisplayName("그룹장은 그룹 이름을 바꿀 수 있다")
    void updateGroupName() {
        // given
        given(fryGroupRepository.findByIdAndMemberUserId(GROUP_ID, OWNER_ID))
                .willReturn(Optional.of(group(OWNER_ID)));

        // when
        GroupNameResponse response =
                groupService.updateGroupName(GROUP_ID, OWNER_ID, new GroupNameUpdateRequest("눅눅한 사람들"));

        // then
        assertThat(response.getName()).isEqualTo("눅눅한 사람들");
    }

    @Test
    @DisplayName("그룹원은 그룹 이름을 바꿀 수 없다")
    void memberCannotUpdateGroupName() {
        // given
        given(fryGroupRepository.findByIdAndMemberUserId(GROUP_ID, MEMBER_ID))
                .willReturn(Optional.of(group(OWNER_ID)));

        // when & then
        assertThatThrownBy(() ->
                groupService.updateGroupName(GROUP_ID, MEMBER_ID, new GroupNameUpdateRequest("눅눅한 사람들")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.GROUP_OWNER_ONLY.getMessage());
    }

    @Test
    @DisplayName("그룹원이 아닌 사람에게는 그룹의 존재를 알리지 않는다")
    void strangerGetsNotFound() {
        // given
        given(fryGroupRepository.findByIdAndMemberUserId(GROUP_ID, MEMBER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                groupService.updateGroupName(GROUP_ID, MEMBER_ID, new GroupNameUpdateRequest("눅눅한 사람들")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.GROUP_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("그룹장은 그룹을 해체할 수 있고 공개 카테고리와 그룹원이 함께 지워진다")
    void deleteGroup() {
        // given
        FryGroup group = group(OWNER_ID);
        given(fryGroupRepository.findByIdAndMemberUserId(GROUP_ID, OWNER_ID)).willReturn(Optional.of(group));

        // when
        groupService.deleteGroup(GROUP_ID, OWNER_ID);

        // then
        then(groupPublicCategoryRepository).should().deleteAllByGroupId(GROUP_ID);
        then(groupMemberRepository).should().deleteAllByGroupId(GROUP_ID);
        then(fryGroupRepository).should().deleteGroupById(GROUP_ID);
    }

    @Test
    @DisplayName("그룹원은 그룹을 해체할 수 없다")
    void memberCannotDeleteGroup() {
        // given
        given(fryGroupRepository.findByIdAndMemberUserId(GROUP_ID, MEMBER_ID))
                .willReturn(Optional.of(group(OWNER_ID)));

        // when & then
        assertThatThrownBy(() -> groupService.deleteGroup(GROUP_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.GROUP_OWNER_ONLY.getMessage());

        then(fryGroupRepository).should(never()).deleteGroupById(anyLong());
        then(groupMemberRepository).should(never()).deleteAllByGroupId(anyLong());
    }

    private FryGroup group(Long ownerId) {
        FryGroup group = FryGroup.builder().name("바삭한 사람들").inviteCode("FRY123").ownerId(ownerId).build();
        ReflectionTestUtils.setField(group, "id", GROUP_ID);
        return group;
    }

}
