package basakan.fryday.controller.admin;

import basakan.fryday.RestDocsSupport;
import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.config.SecurityConfig;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.common.security.JwtAuthenticationFilter;
import basakan.fryday.common.security.JwtTokenProvider;
import basakan.fryday.controller.admin.response.DailyMetricsResponse;
import basakan.fryday.controller.admin.response.DailyMetricsResponse.DailyMetric;
import basakan.fryday.controller.admin.response.WeeklyMetricsResponse;
import basakan.fryday.controller.admin.response.WeeklyMetricsResponse.WeeklyMetric;
import basakan.fryday.service.admin.AdminMetricsService;
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
                new DailyMetric(from, 12, 8, 1),
                new DailyMetric(to, 10, 7, 0))));

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
                                fieldWithPath("data.days[].activeUsers").description("그날 앱을 사용한 유저 수"),
                                fieldWithPath("data.days[].completedUsers").description("그 날짜의 투두를 하나라도 완료한 유저 수"),
                                fieldWithPath("data.days[].signUps").description("그날 가입한 유저 수")
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
                                fieldWithPath("data.weeks[].completedUsers").description("그 주 날짜의 투두를 하나라도 완료한 유저 수"),
                                fieldWithPath("data.weeks[].signUps").description("그 주에 가입한 유저 수")
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
