package basakan.fryday.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BowlTypeTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 29);
    private static final LocalDate PAST = TODAY.minusDays(1);
    private static final LocalDate FUTURE = TODAY.plusDays(1);

    @Test
    @DisplayName("미래 날짜는 투두 유무와 무관하게 그릇이 없다(null)")
    void nullWhenFuture() {
        assertThat(BowlType.calculate(0, 0, FUTURE, TODAY)).isNull();
        assertThat(BowlType.calculate(4, 0, FUTURE, TODAY)).isNull();
        assertThat(BowlType.calculate(4, 2, FUTURE, TODAY)).isNull();
        assertThat(BowlType.calculate(4, 4, FUTURE, TODAY)).isNull();
    }

    @Test
    @DisplayName("투두가 없으면 당일/지난날 무관하게 빈 그릇")
    void emptyWhenNoTodos() {
        assertThat(BowlType.calculate(0, 0, TODAY, TODAY)).isEqualTo(BowlType.EMPTY);
        assertThat(BowlType.calculate(0, 0, PAST, TODAY)).isEqualTo(BowlType.EMPTY);
    }

    @Test
    @DisplayName("모두 완료하면 당일/지난날 무관하게 꽉 찬 그릇")
    void fullWhenAllCompleted() {
        assertThat(BowlType.calculate(3, 3, TODAY, TODAY)).isEqualTo(BowlType.FULL);
        assertThat(BowlType.calculate(3, 3, PAST, TODAY)).isEqualTo(BowlType.FULL);
    }

    @Test
    @DisplayName("당일은 진행 중이면 일부 완료 여부와 무관하게 국자")
    void cookingWhenTodayInProgress() {
        assertThat(BowlType.calculate(4, 1, TODAY, TODAY)).isEqualTo(BowlType.COOKING);
        assertThat(BowlType.calculate(4, 3, TODAY, TODAY)).isEqualTo(BowlType.COOKING);
    }

    @Test
    @DisplayName("당일에 투두가 있고 완료 0개여도 국자")
    void cookingWhenTodayNothingCompleted() {
        assertThat(BowlType.calculate(4, 0, TODAY, TODAY)).isEqualTo(BowlType.COOKING);
    }

    @Test
    @DisplayName("지난날에 투두가 있고 완료 0개면 타버린 그릇")
    void burntWhenPastNothingCompleted() {
        assertThat(BowlType.calculate(4, 0, PAST, TODAY)).isEqualTo(BowlType.BURNT);
    }

    @Test
    @DisplayName("지난날에 완료율 50% 미만이면 적은 그릇")
    void lessWhenPastUnderHalf() {
        assertThat(BowlType.calculate(4, 1, PAST, TODAY)).isEqualTo(BowlType.LESS);
    }

    @Test
    @DisplayName("지난날에 완료율 50% 이상이면 많은 그릇")
    void moreWhenPastHalfOrMore() {
        assertThat(BowlType.calculate(4, 2, PAST, TODAY)).isEqualTo(BowlType.MORE);
        assertThat(BowlType.calculate(4, 3, PAST, TODAY)).isEqualTo(BowlType.MORE);
    }
}
