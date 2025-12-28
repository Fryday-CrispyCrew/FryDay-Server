package basakan.fryday.controller.report;

import basakan.fryday.RestDocsSupport;
import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.config.SecurityConfig;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.common.security.JwtAuthenticationFilter;
import basakan.fryday.common.security.JwtTokenProvider;
import basakan.fryday.domain.category.CategoryColor;
import basakan.fryday.domain.report.AttendanceIcon;
import basakan.fryday.domain.report.MonthlyReport;
import basakan.fryday.domain.report.MonthlyReportCategory;
import basakan.fryday.service.report.MonthlyReportReadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = MonthlyReportController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
        }
)
class MonthlyReportControllerTest extends RestDocsSupport {

    @MockitoBean
    private MonthlyReportReadService monthlyReportReadService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUpSecurityContext() {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(1L, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    @DisplayName("월간 리포트 조회 API - 성공")
    void getMonthlyReport() throws Exception {
        // given
        MonthlyReport report = MonthlyReport.builder()
                .userId(1L)
                .year(2025)
                .month(4)
                .totalTodos(45)
                .completedTodos(30)
                .incompleteTodos(15)
                .achievementRate(66.67)
                .attendanceIcon(AttendanceIcon.GOOD)
                .attendanceMessage("노력이 보여요!")
                .build();

        MonthlyReportCategory category1 = MonthlyReportCategory.builder()
                .categoryName("운동")
                .categoryColor(CategoryColor.BR)
                .totalTodos(15)
                .completedTodos(10)
                .incompleteTodos(5)
                .successRate(66.67)
                .failureRate(33.33)
                .build();

        MonthlyReportCategory category2 = MonthlyReportCategory.builder()
                .categoryName("공부")
                .categoryColor(CategoryColor.CB)
                .totalTodos(20)
                .completedTodos(15)
                .incompleteTodos(5)
                .successRate(75.0)
                .failureRate(25.0)
                .build();

        report.addCategory(category1);
        report.addCategory(category2);

        given(monthlyReportReadService.getMonthlyReport(anyLong(), anyInt(), anyInt()))
                .willReturn(report);

        // when & then
        mockMvc.perform(get("/api/reports/monthly")
                        .param("year", "2025")
                        .param("month", "4"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2025))
                .andExpect(jsonPath("$.month").value(4))
                .andExpect(jsonPath("$.totalTodos").value(45))
                .andExpect(jsonPath("$.completedTodos").value(30))
                .andExpect(jsonPath("$.incompleteTodos").value(15))
                .andExpect(jsonPath("$.achievementRate").value(66.67))
                .andExpect(jsonPath("$.attendanceIcon").value("GOOD"))
                .andExpect(jsonPath("$.attendanceMessage").value("노력이 보여요!"))
                .andExpect(jsonPath("$.categories").isArray())
                .andExpect(jsonPath("$.categories[0].categoryName").value("운동"))
                .andDo(document("monthly-report-get",
                        queryParameters(
                                parameterWithName("year").description("조회할 연도 (예: 2025)"),
                                parameterWithName("month").description("조회할 월 (1~12)")
                        ),
                        responseFields(
                                fieldWithPath("year").type(JsonFieldType.NUMBER).description("연도"),
                                fieldWithPath("month").type(JsonFieldType.NUMBER).description("월"),
                                fieldWithPath("totalTodos").type(JsonFieldType.NUMBER).description("전체 투두 개수"),
                                fieldWithPath("completedTodos").type(JsonFieldType.NUMBER).description("완료한 투두 개수"),
                                fieldWithPath("incompleteTodos").type(JsonFieldType.NUMBER).description("미완료 투두 개수"),
                                fieldWithPath("achievementRate").type(JsonFieldType.NUMBER).description("달성률 (%)"),
                                fieldWithPath("attendanceIcon").type(JsonFieldType.STRING).description("출석 아이콘 (EXCELLENT, GREAT, GOOD, NEEDS_IMPROVEMENT, POOR)"),
                                fieldWithPath("attendanceMessage").type(JsonFieldType.STRING).description("출석 메시지"),
                                fieldWithPath("categories").type(JsonFieldType.ARRAY).description("카테고리별 통계 목록"),
                                fieldWithPath("categories[].categoryName").type(JsonFieldType.STRING).description("카테고리 이름"),
                                fieldWithPath("categories[].categoryColor").type(JsonFieldType.STRING).description("카테고리 색상 코드"),
                                fieldWithPath("categories[].totalTodos").type(JsonFieldType.NUMBER).description("카테고리 전체 투두 개수"),
                                fieldWithPath("categories[].completedTodos").type(JsonFieldType.NUMBER).description("카테고리 완료 투두 개수"),
                                fieldWithPath("categories[].incompleteTodos").type(JsonFieldType.NUMBER).description("카테고리 미완료 투두 개수"),
                                fieldWithPath("categories[].successRate").type(JsonFieldType.NUMBER).description("카테고리 성공률 (%)"),
                                fieldWithPath("categories[].failureRate").type(JsonFieldType.NUMBER).description("카테고리 실패율 (%)")
                        )
                ));
    }

    @Test
    @DisplayName("월간 리포트 조회 API - 현재 월 조회 시 400 에러")
    void getMonthlyReportCurrentMonth() throws Exception {
        // given
        given(monthlyReportReadService.getMonthlyReport(anyLong(), anyInt(), anyInt()))
                .willThrow(new BusinessException(ErrorCode.INVALID_REPORT_PERIOD));

        // when & then
        mockMvc.perform(get("/api/reports/monthly")
                        .param("year", "2025")
                        .param("month", "12"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("현재 월의 리포트는 조회할 수 없습니다."));
    }

    @Test
    @DisplayName("월간 리포트 조회 API - 리포트 없을 시 404 에러")
    void getMonthlyReportNotFound() throws Exception {
        // given
        given(monthlyReportReadService.getMonthlyReport(anyLong(), anyInt(), anyInt()))
                .willThrow(new BusinessException(ErrorCode.REPORT_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/reports/monthly")
                        .param("year", "2025")
                        .param("month", "3"))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("해당 월의 리포트가 존재하지 않습니다."));
    }
}
