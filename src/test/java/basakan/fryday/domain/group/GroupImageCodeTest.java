package basakan.fryday.domain.group;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("그룹 그래픽 코드")
class GroupImageCodeTest {

    @Test
    @DisplayName("배정되는 코드는 항상 01, 02, 03 중 하나다")
    void randomReturnsOnlyKnownCodes() {
        for (int attempt = 0; attempt < 300; attempt++) {
            assertThat(GroupImageCode.random()).isIn("01", "02", "03");
        }
    }

    @Test
    @DisplayName("여러 번 배정하면 세 코드가 모두 나온다")
    void randomEventuallyReturnsEveryCode() {
        Set<String> assigned = new HashSet<>();

        for (int attempt = 0; attempt < 300; attempt++) {
            assigned.add(GroupImageCode.random());
        }

        assertThat(assigned).containsExactlyInAnyOrder("01", "02", "03");
    }
}
