package basakan.fryday.service.admin;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.admin.response.DailyMetricsResponse;
import basakan.fryday.controller.admin.response.DailyMetricsResponse.DailyMetric;
import basakan.fryday.controller.admin.response.WeeklyMetricsResponse;
import basakan.fryday.controller.admin.response.WeeklyMetricsResponse.WeeklyMetric;
import basakan.fryday.repository.admin.AdminMetricsRepository;
import basakan.fryday.repository.admin.AdminMetricsRepository.UserDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("어드민 지표 집계")
class AdminMetricsServiceTest {

    private static final Long TEAM_USER = 99L;

    @Mock
    private AdminMetricsRepository adminMetricsRepository;

    @InjectMocks
    private AdminMetricsService adminMetricsService;

    @Test
    @DisplayName("일별: 기록이 없는 날도 0 으로 채우고, 팀 계정은 뺀다")
    void dailyFillsEmptyDaysAndExcludesInternal() {
        // given
        LocalDate from = LocalDate.of(2026, 10, 20);
        LocalDate to = LocalDate.of(2026, 10, 22);
        given(adminMetricsRepository.findInternalUserIds()).willReturn(Set.of(TEAM_USER));
        given(adminMetricsRepository.findActiveUsers(from, to)).willReturn(List.of(
                row(2026, 10, 20, 1L), row(2026, 10, 20, 2L), row(2026, 10, 20, TEAM_USER),
                row(2026, 10, 22, 1L)));
        given(adminMetricsRepository.findCompletedUsers(from, to)).willReturn(List.of(row(2026, 10, 20, 1L)));
        given(adminMetricsRepository.findSignUps(from, to)).willReturn(List.of(row(2026, 10, 22, 3L)));

        // when
        DailyMetricsResponse response = adminMetricsService.getDailyMetrics(from, to, true);

        // then
        assertThat(response.getDays())
                .extracting(DailyMetric::getDate, DailyMetric::getActiveUsers,
                        DailyMetric::getCompletedUsers, DailyMetric::getSignUps)
                .containsExactly(
                        tuple(LocalDate.of(2026, 10, 20), 2, 1, 0),
                        tuple(LocalDate.of(2026, 10, 21), 0, 0, 0),
                        tuple(LocalDate.of(2026, 10, 22), 1, 0, 1));
    }

    @Test
    @DisplayName("일별: 팀 계정을 포함하면 팀 계정 목록을 조회하지 않고 모두 센다")
    void dailyIncludesInternal() {
        // given
        LocalDate day = LocalDate.of(2026, 10, 20);
        given(adminMetricsRepository.findActiveUsers(day, day)).willReturn(List.of(
                row(2026, 10, 20, 1L), row(2026, 10, 20, TEAM_USER)));
        given(adminMetricsRepository.findCompletedUsers(day, day)).willReturn(List.of());
        given(adminMetricsRepository.findSignUps(day, day)).willReturn(List.of());

        // when
        DailyMetricsResponse response = adminMetricsService.getDailyMetrics(day, day, false);

        // then
        assertThat(response.getDays().get(0).getActiveUsers()).isEqualTo(2);
        verify(adminMetricsRepository, never()).findInternalUserIds();
    }

    @Test
    @DisplayName("주별: 월요일 시작 주로 넓혀서 세고, 한 주에 여러 날 활동한 유저는 한 명으로 센다")
    void weeklyCountsDistinctUsersPerMondayWeek() {
        // given (2026-10-21 수 ~ 2026-10-27 화 → 10/19 월 ~ 11/1 일 두 주)
        LocalDate weekStart = LocalDate.of(2026, 10, 19);
        LocalDate weekEnd = LocalDate.of(2026, 11, 1);
        given(adminMetricsRepository.findInternalUserIds()).willReturn(Set.of());
        given(adminMetricsRepository.findActiveUsers(weekStart, weekEnd)).willReturn(List.of(
                row(2026, 10, 19, 1L), row(2026, 10, 25, 1L), // 같은 주 월, 일
                row(2026, 10, 26, 1L), row(2026, 10, 26, 2L))); // 다음 주 월
        given(adminMetricsRepository.findCompletedUsers(weekStart, weekEnd)).willReturn(List.of(row(2026, 10, 25, 1L)));
        given(adminMetricsRepository.findSignUps(weekStart, weekEnd)).willReturn(List.of());

        // when
        WeeklyMetricsResponse response = adminMetricsService.getWeeklyMetrics(
                LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 27), true);

        // then
        assertThat(response.getWeeks())
                .extracting(WeeklyMetric::getWeekStart, WeeklyMetric::getWeekEnd,
                        WeeklyMetric::getActiveUsers, WeeklyMetric::getCompletedUsers)
                .containsExactly(
                        tuple(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25), 1, 1),
                        tuple(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1), 2, 0));
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦거나 기간이 366일을 넘으면 조회하지 않고 에러를 던진다")
    void invalidPeriod() {
        LocalDate from = LocalDate.of(2026, 10, 20);

        assertThatThrownBy(() -> adminMetricsService.getDailyMetrics(from, from.minusDays(1), true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_METRICS_INVALID_PERIOD);
        assertThatThrownBy(() -> adminMetricsService.getWeeklyMetrics(from, from.plusDays(366), true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_METRICS_INVALID_PERIOD);
        verify(adminMetricsRepository, never()).findActiveUsers(from, from.plusDays(366));
    }

    private UserDate row(int year, int month, int day, Long userId) {
        return new UserDate(LocalDate.of(year, month, day), userId);
    }
}
