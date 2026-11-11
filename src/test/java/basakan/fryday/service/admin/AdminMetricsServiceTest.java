package basakan.fryday.service.admin;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.admin.response.ActivationResponse;
import basakan.fryday.controller.admin.response.DailyMetricsResponse;
import basakan.fryday.controller.admin.response.DailyMetricsResponse.DailyMetric;
import basakan.fryday.controller.admin.response.MetricsSummaryResponse;
import basakan.fryday.controller.admin.response.RetentionResponse;
import basakan.fryday.controller.admin.response.RetentionResponse.Cohort;
import basakan.fryday.controller.admin.response.RetentionResponse.RetentionDay;
import basakan.fryday.controller.admin.response.WeeklyMetricsResponse;
import basakan.fryday.controller.admin.response.WeeklyMetricsResponse.WeeklyMetric;
import basakan.fryday.domain.eventlog.EventType;
import basakan.fryday.repository.admin.AdminMetricsRepository;
import basakan.fryday.repository.admin.AdminMetricsRepository.Account;
import basakan.fryday.repository.admin.AdminMetricsRepository.SignUpEvent;
import basakan.fryday.repository.admin.AdminMetricsRepository.UserDate;
import basakan.fryday.repository.admin.AdminMetricsRepository.UserTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
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
    @DisplayName("일별: 기록이 없는 날도 0 으로 채우고, 7일 평균 DAU, 최근 30일 MAU, 누적 가입자를 팀 계정을 빼고 센다")
    void dailyMetricsExcludeInternal() {
        // given
        LocalDate from = LocalDate.of(2026, 10, 20);
        LocalDate to = LocalDate.of(2026, 10, 22);
        given(adminMetricsRepository.findInternalUserIds()).willReturn(Set.of(TEAM_USER));
        given(adminMetricsRepository.findActiveUsers(LocalDate.of(2026, 9, 21), to)).willReturn(List.of(
                row(2026, 9, 21, 5L), // 10/20 의 30일 범위 첫날
                row(2026, 10, 20, 1L), row(2026, 10, 20, 2L), row(2026, 10, 20, TEAM_USER),
                row(2026, 10, 22, 1L)));
        given(adminMetricsRepository.findCompletedUsers(from, to)).willReturn(List.of(
                row(2026, 10, 20, 1L), row(2026, 10, 20, TEAM_USER)));
        given(adminMetricsRepository.findAccounts()).willReturn(List.of(
                new Account(1L, "a", LocalDateTime.of(2026, 10, 1, 9, 0), true),
                new Account(3L, "c", LocalDateTime.of(2026, 10, 22, 10, 0), false),
                new Account(TEAM_USER, "team", LocalDateTime.of(2026, 10, 21, 9, 0), true)));
        given(adminMetricsRepository.findSignUpEvents()).willReturn(List.of());

        // when
        DailyMetricsResponse response = adminMetricsService.getDailyMetrics(from, to, true);

        // then
        assertThat(response.getDays())
                .extracting(DailyMetric::getDate, DailyMetric::getActiveUsers, DailyMetric::getDauAvg7,
                        DailyMetric::getMau, DailyMetric::getCompletedUsers, DailyMetric::getSignUps,
                        DailyMetric::getCumulativeSignUps, DailyMetric::getCumulativeOnboarded)
                .containsExactly(
                        tuple(LocalDate.of(2026, 10, 20), 2, 0.3, 3, 1, 0, 1, 1),
                        tuple(LocalDate.of(2026, 10, 21), 0, 0.3, 2, 0, 0, 1, 1),
                        tuple(LocalDate.of(2026, 10, 22), 1, 0.4, 2, 0, 1, 2, 1));
    }

    @Test
    @DisplayName("일별: 팀 계정을 포함하면 팀 계정 목록을 조회하지 않고 모두 센다")
    void dailyIncludesInternal() {
        // given
        LocalDate day = LocalDate.of(2026, 10, 20);
        given(adminMetricsRepository.findActiveUsers(day.minusDays(29), day)).willReturn(List.of(
                row(2026, 10, 20, 1L), row(2026, 10, 20, TEAM_USER)));
        given(adminMetricsRepository.findCompletedUsers(day, day)).willReturn(List.of());
        given(adminMetricsRepository.findAccounts()).willReturn(List.of());
        given(adminMetricsRepository.findSignUpEvents()).willReturn(List.of());

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
        given(adminMetricsRepository.findCompletedUsers(weekStart, weekEnd)).willReturn(List.of(
                row(2026, 10, 19, 1L), row(2026, 10, 25, 1L)));
        given(adminMetricsRepository.findAccounts()).willReturn(List.of(
                new Account(1L, "a", LocalDateTime.of(2026, 10, 25, 23, 59), false),
                new Account(2L, "b", LocalDateTime.of(2026, 10, 26, 0, 0), false)));
        given(adminMetricsRepository.findSignUpEvents()).willReturn(List.of());

        // when
        WeeklyMetricsResponse response = adminMetricsService.getWeeklyMetrics(
                LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 27), true);

        // then
        assertThat(response.getWeeks())
                .extracting(WeeklyMetric::getWeekStart, WeeklyMetric::getWeekEnd,
                        WeeklyMetric::getActiveUsers, WeeklyMetric::getCompletedUsers, WeeklyMetric::getSignUps)
                .containsExactly(
                        tuple(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25), 1, 1, 1),
                        tuple(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1), 2, 0, 1));
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

    @Test
    @DisplayName("요약: DAU, 7일 평균 DAU, 최근 30일 MAU, 최근 7일 완료 유저를 팀 계정을 빼고 센다")
    void summary() {
        // given
        LocalDate date = LocalDate.of(2026, 10, 31);
        given(adminMetricsRepository.findInternalUserIds()).willReturn(Set.of(TEAM_USER));
        given(adminMetricsRepository.findAccounts()).willReturn(List.of(
                new Account(1L, "a", LocalDateTime.of(2026, 10, 2, 9, 0), true),
                new Account(2L, "b", LocalDateTime.of(2026, 10, 3, 9, 0), false),
                new Account(TEAM_USER, "team", LocalDateTime.of(2026, 10, 2, 9, 0), true)));
        given(adminMetricsRepository.findSignUpEvents()).willReturn(List.of());
        given(adminMetricsRepository.findActiveUsers(LocalDate.of(2026, 10, 2), date)).willReturn(List.of(
                row(2026, 10, 2, 3L),                          // 30일 범위 시작, MAU 만
                row(2026, 10, 25, 1L),                         // 7일 범위 시작
                row(2026, 10, 31, 1L), row(2026, 10, 31, 2L),
                row(2026, 10, 31, TEAM_USER)));
        given(adminMetricsRepository.findCompletedUsers(LocalDate.of(2026, 10, 25), date)).willReturn(List.of(
                row(2026, 10, 26, 1L), row(2026, 10, 30, 1L), row(2026, 10, 30, TEAM_USER)));
        given(adminMetricsRepository.findActivityTrackedSince()).willReturn(Optional.of(LocalDate.of(2026, 10, 17)));

        // when
        MetricsSummaryResponse response = adminMetricsService.getSummary(date, true);

        // then
        assertThat(response)
                .extracting(MetricsSummaryResponse::getDate,
                        MetricsSummaryResponse::getCumulativeSignUps, MetricsSummaryResponse::getCumulativeOnboarded,
                        MetricsSummaryResponse::getDau, MetricsSummaryResponse::getDauAvg7,
                        MetricsSummaryResponse::getMau, MetricsSummaryResponse::getDauMauRate,
                        MetricsSummaryResponse::getWeeklyCompletedUsers, MetricsSummaryResponse::getActivityTrackedSince)
                .containsExactly(date, 2, 1, 2, 0.4, 3, 66.7, 1, LocalDate.of(2026, 10, 17));
    }

    @Test
    @DisplayName("요약: 활동 기록이 없으면 비율과 평균은 0 이고 수집 시작일은 비어 있다")
    void summaryWithoutActivity() {
        // given
        LocalDate date = LocalDate.of(2026, 10, 31);
        given(adminMetricsRepository.findAccounts()).willReturn(List.of());
        given(adminMetricsRepository.findSignUpEvents()).willReturn(List.of());
        given(adminMetricsRepository.findActiveUsers(LocalDate.of(2026, 10, 2), date)).willReturn(List.of());
        given(adminMetricsRepository.findCompletedUsers(LocalDate.of(2026, 10, 25), date)).willReturn(List.of());
        given(adminMetricsRepository.findActivityTrackedSince()).willReturn(Optional.empty());

        // when
        MetricsSummaryResponse response = adminMetricsService.getSummary(date, false);

        // then
        assertThat(response)
                .extracting(MetricsSummaryResponse::getDauAvg7, MetricsSummaryResponse::getMau,
                        MetricsSummaryResponse::getDauMauRate, MetricsSummaryResponse::getActivityTrackedSince)
                .containsExactly(0.0, 0, 0.0, null);
        verify(adminMetricsRepository, never()).findInternalUserIds();
    }

    @Test
    @DisplayName("요약: 기준일이 오늘(KST)보다 뒤면 조회하지 않고 예외")
    void summaryRejectsFutureDate() {
        // given
        LocalDate tomorrow = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1);

        // when & then
        assertThatThrownBy(() -> adminMetricsService.getSummary(tomorrow, true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_METRICS_INVALID_PERIOD);
        verify(adminMetricsRepository, never()).findAccounts();
    }

    @Test
    @DisplayName("리텐션: 온보딩 완료일을 Day 0 으로, 재가입한 계정의 활동도 같은 사람의 활동으로 보고 팀 계정은 뺀다")
    void retentionByPerson() {
        // given
        given(adminMetricsRepository.findInternalUserIds()).willReturn(Set.of(TEAM_USER));
        given(adminMetricsRepository.findAccounts()).willReturn(List.of(
                new Account(2L, "a", LocalDateTime.of(2026, 9, 5, 9, 0), false), // 1 의 재가입 계정
                new Account(3L, "c", LocalDateTime.of(2026, 9, 1, 9, 0), true),
                new Account(TEAM_USER, "team", LocalDateTime.of(2026, 9, 1, 9, 0), true)));
        given(adminMetricsRepository.findSignUpEvents()).willReturn(List.of(
                new SignUpEvent(1L, EventType.USER_SIGNED_UP, LocalDateTime.of(2026, 8, 31, 23, 0), "a"),
                new SignUpEvent(1L, EventType.ONBOARDING_COMPLETED, LocalDateTime.of(2026, 9, 1, 0, 10), null)));
        given(adminMetricsRepository.findActiveUsers(any(), any())).willReturn(List.of(
                row(2026, 9, 2, 3L),
                row(2026, 9, 8, 2L), // 1 의 D7 을 재가입 계정으로 방문
                row(2026, 9, 2, TEAM_USER)));
        given(adminMetricsRepository.findActivityTrackedSince()).willReturn(Optional.of(LocalDate.of(2026, 8, 1)));

        // when
        RetentionResponse response = adminMetricsService.getRetention(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), CohortUnit.DAY, true);

        // then
        assertThat(response.getCohorts()).singleElement().extracting(Cohort::getSize).isEqualTo(2);
        assertThat(response.getCohorts().get(0).getRetention())
                .extracting(RetentionDay::getDay, RetentionDay::getEligible, RetentionDay::getReturned)
                .containsExactly(tuple(1, 2, 1), tuple(7, 2, 1), tuple(30, 2, 0));
    }

    @Test
    @DisplayName("리텐션: 활동 기록 첫날은 하루가 다 기록되지 않아 측정 대상에서 뺀다")
    void retentionSkipsFirstTrackedDay() {
        // given
        given(adminMetricsRepository.findInternalUserIds()).willReturn(Set.of());
        given(adminMetricsRepository.findAccounts()).willReturn(List.of(
                new Account(1L, "a", LocalDateTime.of(2026, 9, 1, 9, 0), true)));
        given(adminMetricsRepository.findSignUpEvents()).willReturn(List.of());
        given(adminMetricsRepository.findActiveUsers(any(), any())).willReturn(List.of(
                row(2026, 9, 8, 1L)));
        given(adminMetricsRepository.findActivityTrackedSince()).willReturn(Optional.of(LocalDate.of(2026, 9, 2)));

        // when
        RetentionResponse response = adminMetricsService.getRetention(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), CohortUnit.DAY, true);

        // then
        assertThat(response.getCohorts().get(0).getRetention())
                .extracting(RetentionDay::getDay, RetentionDay::getEligible, RetentionDay::getReturned)
                .containsExactly(tuple(1, 0, 0), tuple(7, 1, 1), tuple(30, 1, 0));
    }

    @Test
    @DisplayName("활성화: 가입 완료 후 24시간 안의 투두 생성을 사람 단위로 보고, 팀 계정은 코호트와 사용자 수에서 뺀다")
    void activationByPerson() {
        // given
        given(adminMetricsRepository.findInternalUserIds()).willReturn(Set.of(TEAM_USER));
        given(adminMetricsRepository.findAccounts()).willReturn(List.of(
                new Account(2L, "a", LocalDateTime.of(2026, 9, 1, 11, 0), false), // 1 의 재가입 계정
                new Account(3L, "c", LocalDateTime.of(2026, 9, 1, 9, 0), true),
                new Account(TEAM_USER, "team", LocalDateTime.of(2026, 9, 1, 9, 0), true)));
        given(adminMetricsRepository.findSignUpEvents()).willReturn(List.of(
                new SignUpEvent(1L, EventType.USER_SIGNED_UP, LocalDateTime.of(2026, 8, 31, 23, 0), "a"),
                new SignUpEvent(1L, EventType.ONBOARDING_COMPLETED, LocalDateTime.of(2026, 9, 1, 0, 10), null)));
        given(adminMetricsRepository.findTodoCreatedTimes(any(), any())).willReturn(List.of(
                new UserTime(2L, LocalDateTime.of(2026, 9, 1, 12, 0)), // 1 이 재가입 계정으로 만든 투두
                new UserTime(3L, LocalDateTime.of(2026, 9, 2, 10, 0)), // 24시간이 지난 뒤
                new UserTime(TEAM_USER, LocalDateTime.of(2026, 9, 1, 10, 0))));
        given(adminMetricsRepository.findTodoCreatedUsers(any(), any())).willReturn(List.of(
                row(2026, 9, 1, 2L), row(2026, 9, 2, 3L), row(2026, 9, 3, 3L), row(2026, 9, 1, TEAM_USER)));
        given(adminMetricsRepository.findCompletedUsers(any(), any())).willReturn(List.of(
                row(2026, 9, 1, 3L), row(2026, 9, 1, TEAM_USER)));
        given(adminMetricsRepository.findTodoCreatedTrackedSince()).willReturn(Optional.of(LocalDateTime.of(2026, 8, 1, 0, 0)));

        // when
        ActivationResponse response = adminMetricsService.getActivation(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), CohortUnit.DAY, true);

        // then
        assertThat(response.getTodoCreatedUsers()).isEqualTo(2);
        assertThat(response.getTodoCompletedUsers()).isEqualTo(1);
        assertThat(response.getCohorts())
                .extracting(ActivationResponse.Cohort::getSize, ActivationResponse.Cohort::getEligible,
                        ActivationResponse.Cohort::getActivated, ActivationResponse.Cohort::getRate)
                .containsExactly(tuple(2, 2, 1, 50.0));
    }

    private UserDate row(int year, int month, int day, Long userId) {
        return new UserDate(LocalDate.of(year, month, day), userId);
    }
}
