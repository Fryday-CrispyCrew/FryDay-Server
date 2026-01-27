package basakan.fryday.domain.todo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CharacterStatus 단위 테스트")
class CharacterStatusTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 1, 27);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);

    @Test
    @DisplayName("CASE_A: 투두가 없을 때")
    void determine_caseA() {
        // given
        int totalCount = 0;
        int completedCount = 0;
        LocalDateTime now = LocalDateTime.of(TODAY, java.time.LocalTime.of(15, 0));

        // when
        CharacterStatus result = CharacterStatus.determine(totalCount, completedCount, TODAY, now);

        // then
        assertThat(result).isEqualTo(CharacterStatus.CASE_A);
    }

    @Test
    @DisplayName("CASE_G: 모든 투두가 완료되었을 때")
    void determine_caseG() {
        // given
        int totalCount = 5;
        int completedCount = 5;
        LocalDateTime now = LocalDateTime.of(TODAY, java.time.LocalTime.of(15, 0));

        // when
        CharacterStatus result = CharacterStatus.determine(totalCount, completedCount, TODAY, now);

        // then
        assertThat(result).isEqualTo(CharacterStatus.CASE_G);
    }

    @Test
    @DisplayName("CASE_F: 과거 날짜의 미완료 투두")
    void determine_caseF() {
        // given
        int totalCount = 5;
        int completedCount = 2;
        LocalDateTime now = LocalDateTime.of(TODAY, java.time.LocalTime.of(15, 0));

        // when
        CharacterStatus result = CharacterStatus.determine(totalCount, completedCount, YESTERDAY, now);

        // then
        assertThat(result).isEqualTo(CharacterStatus.CASE_F);
    }

    @Test
    @DisplayName("CASE_E1 또는 CASE_E2: 오늘 날짜, 22시 이후, 미완료 투두 존재")
    void determine_caseE1OrE2() {
        // given
        int totalCount = 5;
        int completedCount = 2;
        LocalDateTime now = LocalDateTime.of(TODAY, java.time.LocalTime.of(22, 30));

        // when
        Set<CharacterStatus> results = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            CharacterStatus result = CharacterStatus.determine(totalCount, completedCount, TODAY, now);
            results.add(result);
        }

        // then
        assertThat(results).containsExactlyInAnyOrder(CharacterStatus.CASE_E1, CharacterStatus.CASE_E2);
        assertThat(results.size()).isEqualTo(2); // E1과 E2 둘 다 나와야 함
    }

    @Test
    @DisplayName("CASE_E는 오늘 날짜가 아니면 적용되지 않음")
    void determine_caseE_onlyForToday() {
        // given
        int totalCount = 5;
        int completedCount = 2;
        LocalDateTime now = LocalDateTime.of(TODAY, java.time.LocalTime.of(22, 30));

        // when - 내일 날짜로 조회
        CharacterStatus result = CharacterStatus.determine(totalCount, completedCount, TOMORROW, now);

        // then - CASE_E가 아닌 다른 케이스가 나와야 함
        assertThat(result).isNotIn(CharacterStatus.CASE_E1, CharacterStatus.CASE_E2);
    }

    @Test
    @DisplayName("CASE_E는 22시 이전에는 적용되지 않음")
    void determine_caseE_onlyAfter22() {
        // given
        int totalCount = 5;
        int completedCount = 2;
        LocalDateTime now = LocalDateTime.of(TODAY, java.time.LocalTime.of(21, 59));

        // when
        CharacterStatus result = CharacterStatus.determine(totalCount, completedCount, TODAY, now);

        // then
        assertThat(result).isNotIn(CharacterStatus.CASE_E1, CharacterStatus.CASE_E2);
    }

    @Test
    @DisplayName("CASE_B: 시작 전 (완료된 투두가 없음, 오늘 또는 미래 날짜)")
    void determine_caseB() {
        // given
        int totalCount = 5;
        int completedCount = 0;
        LocalDateTime now = LocalDateTime.of(TODAY, java.time.LocalTime.of(15, 0));

        // when
        CharacterStatus resultToday = CharacterStatus.determine(totalCount, completedCount, TODAY, now);
        CharacterStatus resultTomorrow = CharacterStatus.determine(totalCount, completedCount, TOMORROW, now);

        // then
        assertThat(resultToday).isEqualTo(CharacterStatus.CASE_B);
        assertThat(resultTomorrow).isEqualTo(CharacterStatus.CASE_B);
    }

    @Test
    @DisplayName("CASE_B: 22시 이후에도 오늘이 아니면 CASE_B")
    void determine_caseB_after22ButNotToday() {
        // given
        int totalCount = 5;
        int completedCount = 0;
        LocalDateTime now = LocalDateTime.of(TODAY, java.time.LocalTime.of(22, 30));

        // when - 내일 날짜로 조회
        CharacterStatus result = CharacterStatus.determine(totalCount, completedCount, TOMORROW, now);

        // then
        assertThat(result).isEqualTo(CharacterStatus.CASE_B);
    }

    @ParameterizedTest
    @CsvSource({
            "5, 1, 20.0",  // 1/5 = 20%
            "10, 4, 40.0", // 4/10 = 40%
            "3, 1, 33.33"  // 1/3 = 33.33%
    })
    @DisplayName("CASE_C: 절반 미만 (완료율 50% 미만)")
    void determine_caseC(int totalCount, int completedCount, double expectedRate) {
        // given
        LocalDateTime now = LocalDateTime.of(TODAY, java.time.LocalTime.of(15, 0));

        // when
        CharacterStatus result = CharacterStatus.determine(totalCount, completedCount, TODAY, now);

        // then
        assertThat(result).isEqualTo(CharacterStatus.CASE_C);
        assertThat((double) completedCount / totalCount * 100).isLessThan(50.0);
    }

    @ParameterizedTest
    @CsvSource({
            "5, 3, 60.0",   // 3/5 = 60%
            "10, 5, 50.0", // 5/10 = 50%
            "10, 6, 60.0", // 6/10 = 60%
            "4, 2, 50.0"   // 2/4 = 50%
    })
    @DisplayName("CASE_D: 절반 이상 (완료율 50% 이상)")
    void determine_caseD(int totalCount, int completedCount, double expectedRate) {
        // given
        LocalDateTime now = LocalDateTime.of(TODAY, java.time.LocalTime.of(15, 0));

        // when
        CharacterStatus result = CharacterStatus.determine(totalCount, completedCount, TODAY, now);

        // then
        assertThat(result).isEqualTo(CharacterStatus.CASE_D);
        assertThat((double) completedCount / totalCount * 100).isGreaterThanOrEqualTo(50.0);
    }

    @Test
    @DisplayName("우선순위: CASE_A가 가장 높음")
    void determine_priority_caseA() {
        // given - totalCount가 0이면 다른 조건과 무관하게 CASE_A
        LocalDateTime now = LocalDateTime.of(TODAY, java.time.LocalTime.of(22, 30));

        // when
        CharacterStatus result = CharacterStatus.determine(0, 0, TODAY, now);

        // then
        assertThat(result).isEqualTo(CharacterStatus.CASE_A);
    }

    @Test
    @DisplayName("우선순위: CASE_G가 두 번째로 높음")
    void determine_priority_caseG() {
        // given - 모든 투두 완료면 과거 날짜여도 CASE_G
        LocalDateTime now = LocalDateTime.of(TODAY, java.time.LocalTime.of(15, 0));

        // when
        CharacterStatus result = CharacterStatus.determine(5, 5, YESTERDAY, now);

        // then
        assertThat(result).isEqualTo(CharacterStatus.CASE_G);
    }

    @Test
    @DisplayName("우선순위: CASE_F가 세 번째로 높음")
    void determine_priority_caseF() {
        // given - 과거 날짜면 22시 이후여도 CASE_F
        LocalDateTime now = LocalDateTime.of(TODAY, java.time.LocalTime.of(22, 30));

        // when
        CharacterStatus result = CharacterStatus.determine(5, 2, YESTERDAY, now);

        // then
        assertThat(result).isEqualTo(CharacterStatus.CASE_F);
    }

    @Test
    @DisplayName("우선순위: CASE_E는 오늘 날짜이고 22시 이후일 때만")
    void determine_priority_caseE() {
        // given
        LocalDateTime now = LocalDateTime.of(TODAY, java.time.LocalTime.of(22, 30));

        // when - 오늘 날짜, 22시 이후, 미완료
        CharacterStatus resultToday = CharacterStatus.determine(5, 2, TODAY, now);
        
        // when - 내일 날짜, 22시 이후, 미완료
        CharacterStatus resultTomorrow = CharacterStatus.determine(5, 2, TOMORROW, now);

        // then
        assertThat(resultToday).isIn(CharacterStatus.CASE_E1, CharacterStatus.CASE_E2);
        assertThat(resultTomorrow).isNotIn(CharacterStatus.CASE_E1, CharacterStatus.CASE_E2);
    }

    @Test
    @DisplayName("CASE_E는 모든 투두가 완료되면 적용되지 않음")
    void determine_caseE_notWhenAllCompleted() {
        // given
        int totalCount = 5;
        int completedCount = 5; // 모두 완료
        LocalDateTime now = LocalDateTime.of(TODAY, java.time.LocalTime.of(22, 30));

        // when
        CharacterStatus result = CharacterStatus.determine(totalCount, completedCount, TODAY, now);

        // then
        assertThat(result).isEqualTo(CharacterStatus.CASE_G); // CASE_G가 우선
        assertThat(result).isNotIn(CharacterStatus.CASE_E1, CharacterStatus.CASE_E2);
    }
}
