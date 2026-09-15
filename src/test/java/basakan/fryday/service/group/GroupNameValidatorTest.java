package basakan.fryday.service.group;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("그룹 이름 검증")
class GroupNameValidatorTest {

    @Test
    @DisplayName("양끝 공백을 제거한 이름을 돌려준다")
    void trimsSurroundingWhitespace() {
        assertThat(GroupNameValidator.validateAndNormalize("  바삭한 사람  ")).isEqualTo("바삭한 사람");
    }

    @Test
    @DisplayName("특수문자는 사용할 수 있다")
    void allowsSpecialCharacters() {
        assertThat(GroupNameValidator.validateAndNormalize("A+B_팀!")).isEqualTo("A+B_팀!");
    }

    @Test
    @DisplayName("10자까지 허용한다")
    void allowsUpToTenCharacters() {
        assertThat(GroupNameValidator.validateAndNormalize("가나다라마바사아자차")).hasSize(10);
    }

    @ParameterizedTest(name = "\"{0}\" 은 거부한다")
    @ValueSource(strings = {"", " ", "   ", "\t"})
    @DisplayName("공백만 있는 이름은 거부한다")
    void rejectsBlankName(String blank) {
        assertThatThrownBy(() -> GroupNameValidator.validateAndNormalize(blank))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_GROUP_NAME.getMessage());
    }

    @Test
    @DisplayName("null 이름은 거부한다")
    void rejectsNullName() {
        assertThatThrownBy(() -> GroupNameValidator.validateAndNormalize(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_GROUP_NAME.getMessage());
    }

    @Test
    @DisplayName("11자 이상은 거부한다")
    void rejectsTooLongName() {
        assertThatThrownBy(() -> GroupNameValidator.validateAndNormalize("가나다라마바사아자차카"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_GROUP_NAME.getMessage());
    }

    @ParameterizedTest(name = "\"{0}\" 은 거부한다")
    @ValueSource(strings = {"바삭한 🍤", "🔥", "가족❤️", "팀☀"})
    @DisplayName("이모지가 섞인 이름은 거부한다")
    void rejectsEmoji(String withEmoji) {
        assertThatThrownBy(() -> GroupNameValidator.validateAndNormalize(withEmoji))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.GROUP_NAME_HAS_EMOJI.getMessage());
    }
}
