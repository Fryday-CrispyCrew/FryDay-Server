package basakan.fryday.service.admin;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.admin.response.DailyMetricsResponse;
import basakan.fryday.controller.admin.response.DailyMetricsResponse.DailyMetric;
import basakan.fryday.controller.admin.response.WeeklyMetricsResponse;
import basakan.fryday.controller.admin.response.WeeklyMetricsResponse.WeeklyMetric;
import basakan.fryday.repository.admin.AdminMetricsRepository;
import basakan.fryday.repository.admin.AdminMetricsRepository.UserDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
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

    private final AdminMetricsRepository adminMetricsRepository;

    public DailyMetricsResponse getDailyMetrics(LocalDate from, LocalDate to, boolean excludeInternal) {
        validatePeriod(from, to);
        Metrics metrics = load(from, to, excludeInternal, UnaryOperator.identity());

        List<DailyMetric> days = from.datesUntil(to.plusDays(1))
                .map(date -> new DailyMetric(date,
                        metrics.activeUsers(date), metrics.completedUsers(date), metrics.signUps(date)))
                .toList();
        return new DailyMetricsResponse(days);
    }

    // 주는 월요일 시작이고, from 과 to 가 속한 주 전체를 센다
    public WeeklyMetricsResponse getWeeklyMetrics(LocalDate from, LocalDate to, boolean excludeInternal) {
        validatePeriod(from, to);
        LocalDate firstWeekStart = weekStart(from);
        LocalDate lastWeekEnd = to.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        Metrics metrics = load(firstWeekStart, lastWeekEnd, excludeInternal, AdminMetricsService::weekStart);

        List<WeeklyMetric> weeks = Stream.iterate(firstWeekStart, start -> start.isBefore(lastWeekEnd),
                        start -> start.plusWeeks(1))
                .map(start -> new WeeklyMetric(start, start.plusDays(6),
                        metrics.activeUsers(start), metrics.completedUsers(start), metrics.signUps(start)))
                .toList();
        return new WeeklyMetricsResponse(weeks);
    }

    private void validatePeriod(LocalDate from, LocalDate to) {
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) + 1 > MAX_PERIOD_DAYS) {
            throw new BusinessException(ErrorCode.ADMIN_METRICS_INVALID_PERIOD);
        }
    }

    private Metrics load(LocalDate from, LocalDate to, boolean excludeInternal, UnaryOperator<LocalDate> bucket) {
        Set<Long> excluded = excludeInternal ? adminMetricsRepository.findInternalUserIds() : Set.of();
        return new Metrics(
                group(adminMetricsRepository.findActiveUsers(from, to), bucket, excluded),
                group(adminMetricsRepository.findCompletedUsers(from, to), bucket, excluded),
                group(adminMetricsRepository.findSignUps(from, to), bucket, excluded)
        );
    }

    // 같은 구간에 여러 번 나온 유저는 한 명으로 센다
    private Map<LocalDate, Set<Long>> group(List<UserDate> rows, UnaryOperator<LocalDate> bucket, Set<Long> excluded) {
        Map<LocalDate, Set<Long>> grouped = new HashMap<>();
        for (UserDate row : rows) {
            if (!excluded.contains(row.userId())) {
                grouped.computeIfAbsent(bucket.apply(row.date()), key -> new HashSet<>()).add(row.userId());
            }
        }
        return grouped;
    }

    private static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private record Metrics(Map<LocalDate, Set<Long>> active,
                           Map<LocalDate, Set<Long>> completed,
                           Map<LocalDate, Set<Long>> signUps) {

        int activeUsers(LocalDate key) {
            return active.getOrDefault(key, Set.of()).size();
        }

        int completedUsers(LocalDate key) {
            return completed.getOrDefault(key, Set.of()).size();
        }

        int signUps(LocalDate key) {
            return signUps.getOrDefault(key, Set.of()).size();
        }
    }
}
