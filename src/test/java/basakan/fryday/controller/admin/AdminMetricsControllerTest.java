package basakan.fryday.controller.admin;

import basakan.fryday.RestDocsSupport;
import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.config.SecurityConfig;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.common.security.JwtAuthenticationFilter;
import basakan.fryday.common.security.JwtTokenProvider;
import basakan.fryday.controller.admin.response.ActivationResponse;
import basakan.fryday.controller.admin.response.DailyMetricsResponse;
import basakan.fryday.controller.admin.response.DailyMetricsResponse.DailyMetric;
import basakan.fryday.controller.admin.response.MetricsSummaryResponse;
import basakan.fryday.controller.admin.response.RetentionResponse;
import basakan.fryday.controller.admin.response.RetentionResponse.Cohort;
import basakan.fryday.controller.admin.response.RetentionResponse.RetentionDay;
import basakan.fryday.controller.admin.response.WeeklyMetricsResponse;
import basakan.fryday.controller.admin.response.WeeklyMetricsResponse.WeeklyMetric;
import basakan.fryday.service.admin.AdminMetricsService;
import basakan.fryday.service.admin.CohortUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.request.ParameterDescriptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AdminMetricsController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
        }
)
class AdminMetricsControllerTest extends RestDocsSupport {

    @MockitoBean
    private AdminMetricsService adminMetricsService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("일별 지표 API - 성공")
    void getDailyMetrics() throws Exception {
        // given
        LocalDate from = LocalDate.of(2026, 10, 20);
        LocalDate to = LocalDate.of(2026, 10, 21);
        given(adminMetricsService.getDailyMetrics(from, to, true)).willReturn(new DailyMetricsResponse(List.of(
                dailyMetric(from, 12, 8, 1, 241),
                dailyMetric(to, 10, 7, 0, 241))));

        // when & then
        mockMvc.perform(get("/api/admin/metrics/daily")
                        .header("Authorization", "Bearer {adminAccessToken}")
                        .param("from", "2026-10-20")
                        .param("to", "2026-10-21")
                        .param("excludeInternal", "true"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].date").value("2026-10-20"))
                .andExpect(jsonPath("$.data.days[0].activeUsers").value(12))
                .andDo(document("admin-metrics-daily",
                        requestHeaders(
                                headerWithName("Authorization").description("어드민 로그인으로 받은 토큰 (Bearer)")
                        ),
                        queryParameters(periodParameters()),
                        responseFields(envelope(
                                fieldWithPath("data.days[].date").description("날짜"),
                                fieldWithPath("data.days[].activeUsers").description("그날 앱을 사용한 유저 수 (DAU)"),
                                fieldWithPath("data.days[].dauAvg7").description("그날 포함 최근 7일 DAU 평균 (소수 첫째 자리)"),
                                fieldWithPath("data.days[].mau").description("그날 포함 최근 30일 동안 하루라도 앱을 사용한 유저 수"),
                                fieldWithPath("data.days[].completedUsers").description("그날 투두를 하나라도 완료한 유저 수 (완료한 시각 기준)"),
                                fieldWithPath("data.days[].signUps").description("그날 처음 가입한 사람 수 (같은 소셜 계정의 재가입 제외)"),
                                fieldWithPath("data.days[].cumulativeSignUps").description("그날까지 가입한 사람 수"),
                                fieldWithPath("data.days[].cumulativeOnboarded").description("그날까지 온보딩을 마친 사람 수")
                        ))
                ));
    }

    @Test
    @DisplayName("주별 지표 API - 성공")
    void getWeeklyMetrics() throws Exception {
        // given
        LocalDate from = LocalDate.of(2026, 10, 19);
        LocalDate to = LocalDate.of(2026, 10, 25);
        given(adminMetricsService.getWeeklyMetrics(from, to, true)).willReturn(new WeeklyMetricsResponse(List.of(
                new WeeklyMetric(from, to, 40, 25, 5))));

        // when & then
        mockMvc.perform(get("/api/admin/metrics/weekly")
                        .header("Authorization", "Bearer {adminAccessToken}")
                        .param("from", "2026-10-19")
                        .param("to", "2026-10-25")
                        .param("excludeInternal", "true"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weeks[0].weekStart").value("2026-10-19"))
                .andExpect(jsonPath("$.data.weeks[0].activeUsers").value(40))
                .andDo(document("admin-metrics-weekly",
                        requestHeaders(
                                headerWithName("Authorization").description("어드민 로그인으로 받은 토큰 (Bearer)")
                        ),
                        queryParameters(periodParameters()),
                        responseFields(envelope(
                                fieldWithPath("data.weeks[].weekStart").description("주 시작일 (월요일)"),
                                fieldWithPath("data.weeks[].weekEnd").description("주 종료일 (일요일)"),
                                fieldWithPath("data.weeks[].activeUsers").description("그 주에 하루라도 앱을 사용한 유저 수 (WAU)"),
                                fieldWithPath("data.weeks[].completedUsers").description("그 주에 투두를 하나라도 완료한 유저 수 (완료한 시각 기준)"),
                                fieldWithPath("data.weeks[].signUps").description("그 주에 처음 가입한 사람 수 (같은 소셜 계정의 재가입 제외)")
                        ))
                ));
    }

    @Test
    @DisplayName("요약 지표 API - 성공")
    void getSummary() throws Exception {
        // given
        LocalDate date = LocalDate.of(2026, 11, 30);
        given(adminMetricsService.getSummary(date, true)).willReturn(MetricsSummaryResponse.builder()
                .date(date)
                .cumulativeSignUps(320)
                .cumulativeOnboarded(290)
                .dau(9)
                .dauAvg7(8.4)
                .mau(75)
                .dauMauRate(12.0)
                .weeklyCompletedUsers(21)
                .activityTrackedSince(LocalDate.of(2026, 10, 17))
                .build());

        // when & then
        mockMvc.perform(get("/api/admin/metrics/summary")
                        .header("Authorization", "Bearer {adminAccessToken}")
                        .param("date", "2026-11-30")
                        .param("excludeInternal", "true"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-11-30"))
                .andExpect(jsonPath("$.data.dauAvg7").value(8.4))
                .andDo(document("admin-metrics-summary",
                        requestHeaders(
                                headerWithName("Authorization").description("어드민 로그인으로 받은 토큰 (Bearer)")
                        ),
                        queryParameters(
                                parameterWithName("date").description("기준일 (yyyy-MM-dd)"),
                                parameterWithName("excludeInternal").description("팀 계정 제외 여부 (기본값 true)").optional()
                        ),
                        responseFields(envelope(
                                fieldWithPath("data.date").description("기준일"),
                                fieldWithPath("data.cumulativeSignUps")
                                        .description("기준일까지 가입한 사람 수. 탈퇴 후 지워진 계정 포함, 같은 소셜 계정의 재가입은 한 사람으로 셈"),
                                fieldWithPath("data.cumulativeOnboarded")
                                        .description("기준일까지 온보딩을 마친 사람 수. 완료 이벤트가 생기기 전 완료한 계정은 가입 시각 기준"),
                                fieldWithPath("data.dau").description("기준일에 앱을 사용한 유저 수"),
                                fieldWithPath("data.dauAvg7").description("기준일 포함 최근 7일 DAU 평균 (소수 첫째 자리)"),
                                fieldWithPath("data.mau").description("기준일 포함 최근 30일 동안 하루라도 앱을 사용한 유저 수"),
                                fieldWithPath("data.dauMauRate").description("DAU ÷ MAU × 100 (소수 첫째 자리). MAU 가 0 이면 0"),
                                fieldWithPath("data.weeklyCompletedUsers").description("기준일 포함 최근 7일 동안 투두를 완료한 유저 수"),
                                fieldWithPath("data.activityTrackedSince")
                                        .description("활동 기록이 처음 쌓인 날짜. 이 날부터 30일이 지나기 전 MAU 는 30일을 다 채우지 못한 값")
                                        .optional()
                        ))
                ));
    }

    @Test
    @DisplayName("코호트 리텐션 API - 성공")
    void getRetention() throws Exception {
        // given
        LocalDate from = LocalDate.of(2026, 12, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);
        given(adminMetricsService.getRetention(from, to, CohortUnit.MONTH, true)).willReturn(new RetentionResponse(List.of(
                Cohort.builder()
                        .cohortStart(from)
                        .cohortEnd(to)
                        .size(120)
                        .retention(List.of(
                                retentionDay(1, 120, 42, 35.0),
                                retentionDay(7, 120, 19, 15.8),
                                retentionDay(30, 35, 3, 8.6)))
                        .build())));

        // when & then
        mockMvc.perform(get("/api/admin/metrics/retention")
                        .header("Authorization", "Bearer {adminAccessToken}")
                        .param("from", "2026-12-01")
                        .param("to", "2026-12-31")
                        .param("cohort", "MONTH")
                        .param("excludeInternal", "true"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cohorts[0].size").value(120))
                .andExpect(jsonPath("$.data.cohorts[0].retention[1].day").value(7))
                .andDo(document("admin-metrics-retention",
                        requestHeaders(
                                headerWithName("Authorization").description("어드민 로그인으로 받은 토큰 (Bearer)")
                        ),
                        queryParameters(
                                parameterWithName("from").description("시작일 (yyyy-MM-dd). 이 날이 속한 코호트부터"),
                                parameterWithName("to").description("종료일 (yyyy-MM-dd). 이 날이 속한 코호트까지, 시작일부터 최대 366일"),
                                parameterWithName("cohort").description("코호트 단위 DAY, WEEK(월요일 시작), MONTH (기본값 WEEK)").optional(),
                                parameterWithName("excludeInternal").description("팀 계정 제외 여부 (기본값 true)").optional()
                        ),
                        responseFields(envelope(
                                fieldWithPath("data.cohorts[].cohortStart").description("코호트 시작일"),
                                fieldWithPath("data.cohorts[].cohortEnd").description("코호트 종료일"),
                                fieldWithPath("data.cohorts[].size").description("이 기간에 가입을 완료(온보딩 완료)한 사람 수"),
                                fieldWithPath("data.cohorts[].retention[].day").description("가입 완료일로부터 며칠째인지 (1, 7, 30)"),
                                fieldWithPath("data.cohorts[].retention[].eligible")
                                        .description("측정 대상 수. 그날이 어제 이전이고 활동 기록이 있는 기간인 사람"),
                                fieldWithPath("data.cohorts[].retention[].returned").description("측정 대상 중 정확히 그날 앱을 사용한 사람 수"),
                                fieldWithPath("data.cohorts[].retention[].rate")
                                        .description("returned ÷ eligible × 100 (소수 첫째 자리). 측정 대상이 없으면 null").optional()
                        ))
                ));
    }

    @Test
    @DisplayName("활성화 지표 API - 성공")
    void getActivation() throws Exception {
        // given
        LocalDate from = LocalDate.of(2026, 11, 2);
        LocalDate to = LocalDate.of(2026, 11, 15);
        given(adminMetricsService.getActivation(from, to, CohortUnit.WEEK, true)).willReturn(ActivationResponse.builder()
                .todoCreatedUsers(40)
                .todoCompletedUsers(31)
                .cohorts(List.of(
                        activationCohort(from, 25, 25, 15, 60.0),
                        activationCohort(from.plusWeeks(1), 18, 0, 0, null)))
                .build());

        // when & then
        mockMvc.perform(get("/api/admin/metrics/activation")
                        .header("Authorization", "Bearer {adminAccessToken}")
                        .param("from", "2026-11-02")
                        .param("to", "2026-11-15")
                        .param("cohort", "WEEK")
                        .param("excludeInternal", "true"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todoCreatedUsers").value(40))
                .andExpect(jsonPath("$.data.cohorts[0].activated").value(15))
                .andDo(document("admin-metrics-activation",
                        requestHeaders(
                                headerWithName("Authorization").description("어드민 로그인으로 받은 토큰 (Bearer)")
                        ),
                        queryParameters(
                                parameterWithName("from").description("시작일 (yyyy-MM-dd)"),
                                parameterWithName("to").description("종료일 (yyyy-MM-dd). 시작일부터 최대 366일"),
                                parameterWithName("cohort").description("코호트 단위 DAY, WEEK(월요일 시작), MONTH (기본값 WEEK)").optional(),
                                parameterWithName("excludeInternal").description("팀 계정 제외 여부 (기본값 true)").optional()
                        ),
                        responseFields(envelope(
                                fieldWithPath("data.todoCreatedUsers").description("시작일부터 종료일까지 투두를 직접 만든 사용자 수"),
                                fieldWithPath("data.todoCompletedUsers").description("시작일부터 종료일까지 투두를 완료한 사용자 수"),
                                fieldWithPath("data.cohorts[].cohortStart").description("코호트 시작일. 시작일이 속한 코호트부터"),
                                fieldWithPath("data.cohorts[].cohortEnd").description("코호트 종료일. 종료일이 속한 코호트까지"),
                                fieldWithPath("data.cohorts[].size").description("이 기간에 가입을 완료(온보딩 완료)한 사람 수"),
                                fieldWithPath("data.cohorts[].eligible")
                                        .description("측정 대상 수. 가입 완료 후 24시간이 지났고 그 24시간이 이벤트 기록 기간 안인 사람"),
                                fieldWithPath("data.cohorts[].activated").description("측정 대상 중 가입 완료 후 24시간 안에 투두를 만든 사람 수"),
                                fieldWithPath("data.cohorts[].rate")
                                        .description("activated ÷ eligible × 100 (소수 첫째 자리). 측정 대상이 없으면 null").optional()
                        ))
                ));
    }

    @Test
    @DisplayName("지표 API - 기간이 올바르지 않으면 400")
    void invalidPeriod() throws Exception {
        // given
        LocalDate from = LocalDate.of(2026, 10, 21);
        LocalDate to = LocalDate.of(2026, 10, 20);
        given(adminMetricsService.getDailyMetrics(from, to, true))
                .willThrow(new BusinessException(ErrorCode.ADMIN_METRICS_INVALID_PERIOD));

        // when & then
        mockMvc.perform(get("/api/admin/metrics/daily")
                        .param("from", "2026-10-21")
                        .param("to", "2026-10-20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorCode.ADMIN_METRICS_INVALID_PERIOD.getMessage()));
    }

    private DailyMetric dailyMetric(LocalDate date, int activeUsers, int completedUsers, int signUps,
                                    int cumulativeSignUps) {
        return DailyMetric.builder()
                .date(date)
                .activeUsers(activeUsers)
                .dauAvg7(11.3)
                .mau(52)
                .completedUsers(completedUsers)
                .signUps(signUps)
                .cumulativeSignUps(cumulativeSignUps)
                .cumulativeOnboarded(230)
                .build();
    }

    private ActivationResponse.Cohort activationCohort(LocalDate start, int size, int eligible, int activated,
                                                       Double rate) {
        return ActivationResponse.Cohort.builder()
                .cohortStart(start)
                .cohortEnd(start.plusDays(6))
                .size(size)
                .eligible(eligible)
                .activated(activated)
                .rate(rate)
                .build();
    }

    private RetentionDay retentionDay(int day, int eligible, int returned, Double rate) {
        return RetentionDay.builder().day(day).eligible(eligible).returned(returned).rate(rate).build();
    }

    private ParameterDescriptor[] periodParameters() {
        return new ParameterDescriptor[]{
                parameterWithName("from").description("시작일 (yyyy-MM-dd)"),
                parameterWithName("to").description("종료일 (yyyy-MM-dd). 시작일부터 최대 366일"),
                parameterWithName("excludeInternal").description("팀 계정 제외 여부 (기본값 true)").optional()
        };
    }

    private FieldDescriptor[] envelope(FieldDescriptor... dataFields) {
        List<FieldDescriptor> fields = new ArrayList<>(List.of(
                fieldWithPath("success").description("성공 여부"),
                fieldWithPath("message").description("응답 메시지"),
                fieldWithPath("timestamp").description("응답 시각")));
        fields.addAll(List.of(dataFields));
        return fields.toArray(FieldDescriptor[]::new);
    }
}
