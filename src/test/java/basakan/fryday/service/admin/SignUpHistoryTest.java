package basakan.fryday.service.admin;

import basakan.fryday.domain.eventlog.EventType;
import basakan.fryday.repository.admin.AdminMetricsRepository.Account;
import basakan.fryday.repository.admin.AdminMetricsRepository.SignUpEvent;
import basakan.fryday.service.admin.SignUpHistory.Counts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("가입 이력")
class SignUpHistoryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);
    private static final LocalDateTime END = DAY.plusDays(1).atStartOfDay();

    @Test
    @DisplayName("남아 있는 계정과 지워진 계정의 가입 이벤트를 합치고, 같은 계정은 한 번만 센다")
    void mergesAccountsAndSignUpEvents() {
        // given
        List<Account> accounts = List.of(
                account(1L, "a", DAY.atTime(9, 0), false),
                account(2L, "b", DAY.atTime(10, 0), false));
        List<SignUpEvent> events = List.of(
                signUp(2L, "b", DAY.atTime(10, 0)),
                signUp(3L, "c", DAY.atTime(11, 0)),  // 탈퇴 후 지워진 계정
                signUp(4L, null, DAY.atTime(12, 0))); // 키를 남기기 전에 가입하고 지워진 계정

        // when & then
        assertThat(count(accounts, events, Set.of())).isEqualTo(new Counts(4, 0));
    }

    @Test
    @DisplayName("기준일 다음 날 0시 이후 가입은 세지 않는다")
    void excludesSignUpsAfterDate() {
        // given
        List<Account> accounts = List.of(
                account(1L, "a", END.minusNanos(1000), false),
                account(2L, "b", END, false));

        // when & then
        assertThat(count(accounts, List.of(), Set.of())).isEqualTo(new Counts(1, 0));
    }

    @Test
    @DisplayName("같은 소셜 계정으로 여러 번 재가입해도 한 사람으로 센다")
    void countsReregistrationsAsOnePerson() {
        // given
        List<Account> accounts = List.of(
                account(3L, "a", DAY.atTime(9, 0), false));
        List<SignUpEvent> events = List.of(
                signUp(1L, "a", DAY.minusDays(40).atStartOfDay()),
                signUp(2L, "a", DAY.minusDays(20).atStartOfDay()),
                signUp(3L, "a", DAY.atTime(9, 0)));

        // when & then
        assertThat(count(accounts, events, Set.of())).isEqualTo(new Counts(1, 0));
    }

    @Test
    @DisplayName("재가입한 사람은 어느 계정에서든 온보딩을 마쳤으면 한 번 센다")
    void reregisteredOnboardingCountsOnce() {
        // given
        List<SignUpEvent> events = List.of(
                signUp(1L, "a", DAY.minusDays(40).atStartOfDay()),
                signUp(2L, "a", DAY.minusDays(20).atStartOfDay()),
                signUp(3L, "a", DAY.atTime(9, 0)),
                onboarded(1L, DAY.minusDays(40).atTime(0, 5)),
                onboarded(3L, DAY.atTime(9, 5)));

        // when & then
        assertThat(count(List.of(), events, Set.of())).isEqualTo(new Counts(1, 1));
    }

    @Test
    @DisplayName("온보딩은 완료 이벤트 시각으로 보고, 이벤트가 없는 완료 계정은 가입 시각으로 본다")
    void onboardingUsesEventTimeOrCreatedAt() {
        // given
        List<Account> accounts = List.of(
                account(1L, "a", DAY.minusDays(30).atStartOfDay(), true), // 이벤트 기록 전 완료
                account(2L, "b", DAY.atTime(9, 0), true),                 // 기준일 다음 날 완료
                account(3L, "c", DAY.atTime(9, 0), false));
        List<SignUpEvent> events = List.of(
                onboarded(2L, END.plusHours(1)));

        // when & then
        assertThat(count(accounts, events, Set.of())).isEqualTo(new Counts(3, 1));
    }

    @Test
    @DisplayName("팀 계정은 빼고, 팀원이 재가입한 새 계정도 같은 사람이면 뺀다")
    void excludesInternalPeople() {
        // given
        List<Account> accounts = List.of(
                account(1L, "a", DAY.atTime(9, 0), true),
                account(100L, "team", DAY.atTime(9, 0), true));
        List<SignUpEvent> events = List.of(
                signUp(99L, "team", DAY.minusDays(20).atStartOfDay())); // 팀 계정 목록에 있는 예전 계정

        // when & then
        assertThat(count(accounts, events, Set.of(99L))).isEqualTo(new Counts(1, 1));
    }

    private Counts count(List<Account> accounts, List<SignUpEvent> events, Set<Long> excluded) {
        return SignUpHistory.of(accounts, events, excluded).countBefore(END);
    }

    private Account account(Long userId, String accountKey, LocalDateTime createdAt, boolean onboardingCompleted) {
        return new Account(userId, accountKey, createdAt, onboardingCompleted);
    }

    private SignUpEvent signUp(Long userId, String accountKey, LocalDateTime at) {
        return new SignUpEvent(userId, EventType.USER_SIGNED_UP, at, accountKey);
    }

    private SignUpEvent onboarded(Long userId, LocalDateTime at) {
        return new SignUpEvent(userId, EventType.ONBOARDING_COMPLETED, at, null);
    }
}
