package basakan.fryday.service.admin;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.admin.response.ActivationResponse;
import basakan.fryday.controller.admin.response.DailyMetricsResponse;
import basakan.fryday.controller.admin.response.DailyMetricsResponse.DailyMetric;
import basakan.fryday.controller.admin.response.MetricsSummaryResponse;
import basakan.fryday.controller.admin.response.RetentionResponse;
import basakan.fryday.controller.admin.response.WeeklyMetricsResponse;
import basakan.fryday.controller.admin.response.WeeklyMetricsResponse.WeeklyMetric;
import basakan.fryday.repository.admin.AdminMetricsRepository;
import basakan.fryday.repository.admin.AdminMetricsRepository.UserDate;
import basakan.fryday.repository.admin.AdminMetricsRepository.UserTime;
import basakan.fryday.service.admin.SignUpHistory.Counts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMetricsService {

    private static final long MAX_PERIOD_DAYS = 366;
    private static final int MAU_DAYS = 30;
    private static final int DAU_AVERAGE_DAYS = 7;
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final AdminMetricsRepository adminMetricsRepository;

    public DailyMetricsResponse getDailyMetrics(LocalDate from, LocalDate to, boolean excludeInternal) {
        validatePeriod(from, to);
        Set<Long> excluded = excludedUsers(excludeInternal);
        // 기간 첫날의 MAU 를 세려면 그 전 29일 활동도 필요하다
        ActiveUsers active = activeUsers(from.minusDays(MAU_DAYS - 1), to, excluded);
        Map<LocalDate, Set<Long>> completed = group(
                adminMetricsRepository.findCompletedUsers(from, to), UnaryOperator.identity(), excluded);
        SignUpHistory signUps = signUpHistory(excluded);

        List<DailyMetric> days = from.datesUntil(to.plusDays(1))
                .map(date -> {
                    Counts upToDate = signUps.countBefore(date.plusDays(1).atStartOfDay());
                    Counts newOnDate = upToDate.minus(signUps.countBefore(date.atStartOfDay()));
                    return DailyMetric.builder()
                            .date(date)
                            .activeUsers(active.dau(date))
                            .dauAvg7(active.dauAvg7(date))
                            .mau(active.mau(date))
                            .completedUsers(sizeOf(completed, date))
                            .signUps(newOnDate.signedUp())
                            .cumulativeSignUps(upToDate.signedUp())
                            .cumulativeOnboarded(upToDate.onboarded())
                            .build();
                })
                .toList();
        return new DailyMetricsResponse(days);
    }

    // 주는 월요일 시작이고, from 과 to 가 속한 주 전체를 센다
    public WeeklyMetricsResponse getWeeklyMetrics(LocalDate from, LocalDate to, boolean excludeInternal) {
        validatePeriod(from, to);
        LocalDate firstWeekStart = weekStart(from);
        LocalDate lastWeekEnd = to.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        Set<Long> excluded = excludedUsers(excludeInternal);
        Map<LocalDate, Set<Long>> active = group(
                adminMetricsRepository.findActiveUsers(firstWeekStart, lastWeekEnd),
                AdminMetricsService::weekStart, excluded);
        Map<LocalDate, Set<Long>> completed = group(
                adminMetricsRepository.findCompletedUsers(firstWeekStart, lastWeekEnd),
                AdminMetricsService::weekStart, excluded);
        SignUpHistory signUps = signUpHistory(excluded);

        List<WeeklyMetric> weeks = Stream.iterate(firstWeekStart, start -> start.isBefore(lastWeekEnd),
                        start -> start.plusWeeks(1))
                .map(start -> new WeeklyMetric(start, start.plusDays(6),
                        sizeOf(active, start), sizeOf(completed, start),
                        signUps.countBefore(start.plusWeeks(1).atStartOfDay())
                                .minus(signUps.countBefore(start.atStartOfDay())).signedUp()))
                .toList();
        return new WeeklyMetricsResponse(weeks);
    }

    public MetricsSummaryResponse getSummary(LocalDate date, boolean excludeInternal) {
        if (date.isAfter(LocalDate.now(KOREA_ZONE))) {
            throw new BusinessException(ErrorCode.ADMIN_METRICS_INVALID_PERIOD);
        }
        Set<Long> excluded = excludedUsers(excludeInternal);
        Counts signUps = signUpHistory(excluded).countBefore(date.plusDays(1).atStartOfDay());
        ActiveUsers active = activeUsers(date.minusDays(MAU_DAYS - 1), date, excluded);
        int dau = active.dau(date);
        int mau = active.mau(date);

        int weeklyCompleted = countUsers(
                adminMetricsRepository.findCompletedUsers(date.minusDays(DAU_AVERAGE_DAYS - 1), date), excluded);

        return MetricsSummaryResponse.builder()
                .date(date)
                .cumulativeSignUps(signUps.signedUp())
                .cumulativeOnboarded(signUps.onboarded())
                .dau(dau)
                .dauAvg7(active.dauAvg7(date))
                .mau(mau)
                .dauMauRate(mau == 0 ? 0 : roundOneDecimal(dau * 100.0 / mau))
                .weeklyCompletedUsers(weeklyCompleted)
                .activityTrackedSince(adminMetricsRepository.findActivityTrackedSince().orElse(null))
                .build();
    }

    // from 과 to 가 속한 코호트 전체를 센다
    public RetentionResponse getRetention(LocalDate from, LocalDate to, CohortUnit unit, boolean excludeInternal) {
        validatePeriod(from, to);
        SignUpHistory history = signUpHistory(excludedUsers(excludeInternal));
        LocalDate cohortFrom = unit.start(from);
        LocalDate cohortTo = unit.end(unit.start(to));
        Map<String, LocalDate> day0ByPerson = new HashMap<>();
        onboardedWithin(history, cohortFrom, cohortTo)
                .forEach((person, onboardedAt) -> day0ByPerson.put(person, onboardedAt.toLocalDate()));

        LocalDate yesterday = LocalDate.now(KOREA_ZONE).minusDays(1);
        LocalDate activityFrom = cohortFrom.plusDays(1);
        LocalDate activityTo = min(cohortTo.plusDays(CohortRetention.LONGEST_DAY), yesterday);
        Map<String, Set<LocalDate>> activeDates = new HashMap<>();
        if (!activityFrom.isAfter(activityTo)) {
            for (UserDate row : adminMetricsRepository.findActiveUsers(activityFrom, activityTo)) {
                String person = history.personOf(row.userId());
                if (day0ByPerson.containsKey(person)) {
                    activeDates.computeIfAbsent(person, key -> new HashSet<>()).add(row.date());
                }
            }
        }

        // 기록 첫날은 배포 시점 이후 활동만 남아 있어서 측정에서 뺀다
        LocalDate measurableFrom = adminMetricsRepository.findActivityTrackedSince()
                .map(trackedSince -> trackedSince.plusDays(1))
                .orElse(null);
        return new RetentionResponse(CohortRetention.calculate(day0ByPerson, activeDates, unit, from, to,
                measurableFrom, yesterday));
    }

    // 코호트는 from 과 to 가 속한 단위 전체, 투두 생성 사용자 수는 from 부터 to 까지 센다
    public ActivationResponse getActivation(LocalDate from, LocalDate to, CohortUnit unit, boolean excludeInternal) {
        validatePeriod(from, to);
        Set<Long> excluded = excludedUsers(excludeInternal);
        SignUpHistory history = signUpHistory(excluded);
        LocalDate cohortFrom = unit.start(from);
        LocalDate cohortTo = unit.end(unit.start(to));
        Map<String, LocalDateTime> onboardedAtByPerson = onboardedWithin(history, cohortFrom, cohortTo);

        Map<String, LocalDateTime> firstTodoAt = new HashMap<>();
        List<UserTime> created = adminMetricsRepository.findTodoCreatedTimes(
                cohortFrom.atStartOfDay(), cohortTo.plusDays(1).atStartOfDay().plus(ActivationRate.WINDOW));
        for (UserTime row : created) {
            String person = history.personOf(row.userId());
            if (onboardedAtByPerson.containsKey(person)) {
                firstTodoAt.merge(person, row.occurredAt(), (a, b) -> a.isBefore(b) ? a : b);
            }
        }

        return ActivationResponse.builder()
                .todoCreatedUsers(countUsers(adminMetricsRepository.findTodoCreatedUsers(from, to), excluded))
                .todoCompletedUsers(countUsers(adminMetricsRepository.findCompletedUsers(from, to), excluded))
                .cohorts(ActivationRate.calculate(onboardedAtByPerson, firstTodoAt, unit, from, to,
                        adminMetricsRepository.findTodoCreatedTrackedSince().orElse(null),
                        LocalDateTime.now(KOREA_ZONE)))
                .build();
    }

    private static Map<String, LocalDateTime> onboardedWithin(SignUpHistory history, LocalDate from, LocalDate to) {
        Map<String, LocalDateTime> within = new HashMap<>();
        history.onboardedAt().forEach((person, onboardedAt) -> {
            LocalDate date = onboardedAt.toLocalDate();
            if (!date.isBefore(from) && !date.isAfter(to)) {
                within.put(person, onboardedAt);
            }
        });
        return within;
    }

    private static int countUsers(List<UserDate> rows, Set<Long> excluded) {
        return (int) rows.stream()
                .map(UserDate::userId)
                .filter(userId -> !excluded.contains(userId))
                .distinct()
                .count();
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private void validatePeriod(LocalDate from, LocalDate to) {
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) + 1 > MAX_PERIOD_DAYS) {
            throw new BusinessException(ErrorCode.ADMIN_METRICS_INVALID_PERIOD);
        }
    }

    private Set<Long> excludedUsers(boolean excludeInternal) {
        return excludeInternal ? adminMetricsRepository.findInternalUserIds() : Set.of();
    }

    private ActiveUsers activeUsers(LocalDate from, LocalDate to, Set<Long> excluded) {
        return new ActiveUsers(group(adminMetricsRepository.findActiveUsers(from, to), UnaryOperator.identity(), excluded));
    }

    private SignUpHistory signUpHistory(Set<Long> excluded) {
        return SignUpHistory.of(
                adminMetricsRepository.findAccounts(), adminMetricsRepository.findSignUpEvents(), excluded);
    }

    // 같은 구간에 여러 번 나온 유저는 한 명으로 센다
    private static Map<LocalDate, Set<Long>> group(List<UserDate> rows, UnaryOperator<LocalDate> bucket,
                                                   Set<Long> excluded) {
        Map<LocalDate, Set<Long>> grouped = new HashMap<>();
        for (UserDate row : rows) {
            if (!excluded.contains(row.userId())) {
                grouped.computeIfAbsent(bucket.apply(row.date()), key -> new HashSet<>()).add(row.userId());
            }
        }
        return grouped;
    }

    private static int sizeOf(Map<LocalDate, Set<Long>> grouped, LocalDate key) {
        return grouped.getOrDefault(key, Set.of()).size();
    }

    private static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static double roundOneDecimal(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private record ActiveUsers(Map<LocalDate, Set<Long>> byDate) {

        int dau(LocalDate date) {
            return sizeOf(byDate, date);
        }

        double dauAvg7(LocalDate date) {
            return roundOneDecimal(date.minusDays(DAU_AVERAGE_DAYS - 1).datesUntil(date.plusDays(1))
                    .mapToInt(this::dau)
                    .average().orElse(0));
        }

        int mau(LocalDate date) {
            Set<Long> users = new HashSet<>();
            date.minusDays(MAU_DAYS - 1).datesUntil(date.plusDays(1))
                    .forEach(day -> users.addAll(byDate.getOrDefault(day, Set.of())));
            return users.size();
        }
    }
}
