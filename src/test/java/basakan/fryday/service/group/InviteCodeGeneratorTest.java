package basakan.fryday.service.group;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.repository.group.FryGroupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("초대 코드 생성기")
class InviteCodeGeneratorTest {

    private static final String CODE_PATTERN = "^[A-Z0-9]{6}$";

    @Mock
    private FryGroupRepository fryGroupRepository;

    @InjectMocks
    private InviteCodeGenerator inviteCodeGenerator;

    @Test
    @DisplayName("영문 대문자와 숫자로 이루어진 6자리 코드를 만든다")
    void generatesSixCharacterCode() {
        // given
        given(fryGroupRepository.existsByInviteCode(anyString())).willReturn(false);

        // when
        String code = inviteCodeGenerator.generate();

        // then
        assertThat(code).hasSize(6).matches(CODE_PATTERN);
    }

    @Test
    @DisplayName("반복 생성해도 항상 형식을 지킨다")
    void generatedCodesAlwaysMatchFormat() {
        // given
        given(fryGroupRepository.existsByInviteCode(anyString())).willReturn(false);

        // when & then
        for (int i = 0; i < 1000; i++) {
            assertThat(inviteCodeGenerator.generate()).matches(CODE_PATTERN);
        }
    }

    @Test
    @DisplayName("이미 사용 중인 코드가 나오면 사용 가능한 코드가 나올 때까지 다시 만든다")
    void retriesUntilUnusedCodeFound() {
        // given
        given(fryGroupRepository.existsByInviteCode(anyString()))
                .willReturn(true)
                .willReturn(true)
                .willReturn(false);

        // when
        String code = inviteCodeGenerator.generate();

        // then
        assertThat(code).matches(CODE_PATTERN);
        then(fryGroupRepository).should(times(3)).existsByInviteCode(anyString());
    }

    @Test
    @DisplayName("최대 재시도 횟수만큼 모두 중복이면 예외를 던진다")
    void throwsWhenAllAttemptsCollide() {
        // given
        given(fryGroupRepository.existsByInviteCode(anyString())).willReturn(true);

        // when & then
        assertThatThrownBy(() -> inviteCodeGenerator.generate())
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVITE_CODE_GENERATION_FAILED.getMessage());
        then(fryGroupRepository).should(times(5)).existsByInviteCode(anyString());
    }
}
