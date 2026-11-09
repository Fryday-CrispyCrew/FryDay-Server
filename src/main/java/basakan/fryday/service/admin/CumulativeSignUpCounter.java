package basakan.fryday.service.admin;

import basakan.fryday.domain.eventlog.EventType;
import basakan.fryday.repository.admin.AdminMetricsRepository.Account;
import basakan.fryday.repository.admin.AdminMetricsRepository.SignUpEvent;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 계정이 아니라 사람(소셜 계정) 단위로 센다. 탈퇴 7일 뒤 재가입하면 새 계정 ID 가 생기기 때문이다.
 * 탈퇴 후 지워진 계정은 가입 이벤트에만, 가입 이벤트가 생기기 전 계정은 users 에만 있어서 둘을 합친다.
 * 온보딩 완료 이벤트가 생기기 전에 완료한 계정은 완료 시각을 알 수 없어 가입 시각으로 본다.
 */
final class CumulativeSignUpCounter {

    private final Collection<LocalDateTime> signedUpAt;
    private final Collection<LocalDateTime> onboardedAt;

    private CumulativeSignUpCounter(Collection<LocalDateTime> signedUpAt, Collection<LocalDateTime> onboardedAt) {
        this.signedUpAt = signedUpAt;
        this.onboardedAt = onboardedAt;
    }

    static CumulativeSignUpCounter of(List<Account> accounts, List<SignUpEvent> events, Set<Long> excluded) {
        Map<Long, String> people = new HashMap<>();
        for (Account account : accounts) {
            people.put(account.userId(), account.accountKey());
        }
        for (SignUpEvent event : events) {
            if (event.type() == EventType.USER_SIGNED_UP && event.accountKey() != null) {
                people.putIfAbsent(event.userId(), event.accountKey());
            }
        }

        Map<String, LocalDateTime> signedUpAt = new HashMap<>();
        Map<String, LocalDateTime> onboardedAt = new HashMap<>();
        Set<Long> onboardingRecorded = new HashSet<>();
        for (SignUpEvent event : events) {
            String person = personOf(people, event.userId());
            if (event.type() == EventType.USER_SIGNED_UP) {
                signedUpAt.merge(person, event.occurredAt(), CumulativeSignUpCounter::earlier);
            } else if (event.type() == EventType.ONBOARDING_COMPLETED) {
                onboardedAt.merge(person, event.occurredAt(), CumulativeSignUpCounter::earlier);
                onboardingRecorded.add(event.userId());
            }
        }
        for (Account account : accounts) {
            LocalDateTime createdAt = account.createdAt() == null ? LocalDateTime.MIN : account.createdAt();
            signedUpAt.merge(account.accountKey(), createdAt, CumulativeSignUpCounter::earlier);
            if (account.onboardingCompleted() && !onboardingRecorded.contains(account.userId())) {
                onboardedAt.merge(account.accountKey(), createdAt, CumulativeSignUpCounter::earlier);
            }
        }

        // 팀원이 재가입해 새 계정 ID 가 팀 계정 목록에 없어도 같은 사람이면 뺀다
        for (Long userId : excluded) {
            String person = personOf(people, userId);
            signedUpAt.remove(person);
            onboardedAt.remove(person);
        }
        // 가입 기록 없이 온보딩 이벤트만 남은 사람은 셀 수 없다
        onboardedAt.keySet().retainAll(signedUpAt.keySet());
        return new CumulativeSignUpCounter(signedUpAt.values(), onboardedAt.values());
    }

    Counts countBefore(LocalDateTime endExclusive) {
        return new Counts(countBefore(signedUpAt, endExclusive), countBefore(onboardedAt, endExclusive));
    }

    private static int countBefore(Collection<LocalDateTime> times, LocalDateTime endExclusive) {
        return (int) times.stream().filter(time -> time.isBefore(endExclusive)).count();
    }

    // 키가 없는 계정은 이미 지워져 소셜 계정을 알 수 없으므로 계정 하나를 한 사람으로 본다
    private static String personOf(Map<Long, String> people, Long userId) {
        return people.getOrDefault(userId, "user:" + userId);
    }

    private static LocalDateTime earlier(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    record Counts(int signedUp, int onboarded) {

        Counts minus(Counts other) {
            return new Counts(signedUp - other.signedUp, onboarded - other.onboarded);
        }
    }
}
