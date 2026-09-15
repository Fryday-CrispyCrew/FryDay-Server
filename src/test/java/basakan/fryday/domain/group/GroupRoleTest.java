package basakan.fryday.domain.group;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("그룹 권한")
class GroupRoleTest {

    private static final Long OWNER_ID = 1L;
    private static final Long MEMBER_ID = 2L;

    @Test
    @DisplayName("그룹을 만든 사용자는 그룹장이다")
    void ownerIsOwner() {
        FryGroup group = FryGroup.builder().name("바삭한 사람들").inviteCode("FRY123").ownerId(OWNER_ID).build();

        assertThat(GroupRole.of(group, OWNER_ID)).isEqualTo(GroupRole.OWNER);
        assertThat(group.isOwner(OWNER_ID)).isTrue();
    }

    @Test
    @DisplayName("그룹을 만들지 않은 사용자는 그룹원이다")
    void otherUserIsMember() {
        FryGroup group = FryGroup.builder().name("바삭한 사람들").inviteCode("FRY123").ownerId(OWNER_ID).build();

        assertThat(GroupRole.of(group, MEMBER_ID)).isEqualTo(GroupRole.MEMBER);
        assertThat(group.isOwner(MEMBER_ID)).isFalse();
    }

    @Test
    @DisplayName("그룹 이름은 변경할 수 있다")
    void updateName() {
        FryGroup group = FryGroup.builder().name("바삭한 사람들").inviteCode("FRY123").ownerId(OWNER_ID).build();

        group.updateName("눅눅한 사람들");

        assertThat(group.getName()).isEqualTo("눅눅한 사람들");
    }
}
