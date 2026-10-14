package basakan.fryday.domain.group;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("그룹원 영업 상태")
class GroupMemberStatusTest {

    @ParameterizedTest(name = "전체 {0}, 완료 {1} → {2}, {3}")
    @CsvSource({
            "0, 0, BEFORE_OPEN, KNOCK",
            "3, 0, PREPARING, ORDER",
            "3, 1, FRYING, DELICIOUS",
            "3, 2, FRYING, DELICIOUS",
            "3, 3, CLOSED, APPLAUSE",
            "1, 1, CLOSED, APPLAUSE"
    })
    @DisplayName("오늘 공개 투두 개수로 영업 상태와 보낼 수 있는 상호작용을 정한다")
    void statusFromTodoCounts(int total, int completed, GroupMemberStatus status, GroupInteractionType interaction) {
        GroupMemberStatus actual = GroupMemberStatus.of(total, completed);

        assertThat(actual).isEqualTo(status);
        assertThat(actual.getAvailableInteraction()).isEqualTo(interaction);
    }
}
