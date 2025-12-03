package basakan.fryday.controller;

import basakan.fryday.RestDocsSupport;
import basakan.fryday.controller.todo.response.DailyResultResponse;
import basakan.fryday.domain.DailyResult;
import basakan.fryday.domain.BowlType;
import basakan.fryday.service.DailyResultService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DailyResultController.class)
class DailyResultControllerTest extends RestDocsSupport {

    @MockitoBean
    private DailyResultService dailyResultService;

    @Test
    @DisplayName("그릇 결과 조회 API")
    void getDailyResults() throws Exception {
        // given
        LocalDate startDate = LocalDate.of(2025, 11, 1);
        LocalDate endDate = LocalDate.of(2025, 11, 30);

        DailyResult result1 = DailyResult.builder()
                .userId(1L)
                .date(LocalDate.of(2025, 11, 1))
                .bowlType(BowlType.FULL)
                .build();

        DailyResult result2 = DailyResult.builder()
                .userId(1L)
                .date(LocalDate.of(2025, 11, 2))
                .bowlType(BowlType.EMPTY)
                .build();

        List<DailyResultResponse> responses = List.of(
                DailyResultResponse.from(result1),
                DailyResultResponse.from(result2)
        );

        given(dailyResultService.getDailyResults(anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(responses);

        mockMvc.perform(get("/api/daily-results")
                    .param("startDate", startDate.toString())
                    .param("endDate", endDate.toString())
                    .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("daily-result-list",
                        queryParameters(
                                parameterWithName("startDate").description("조회 시작 날짜 (YYYY-MM-DD)"),
                                parameterWithName("endDate").description("조회 종료 날짜 (YYYY-MM-DD)")
                        ),

                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),

                                fieldWithPath("data[].date").type(JsonFieldType.STRING).description("날짜"),
                                fieldWithPath("data[].bowlType").type(JsonFieldType.STRING).description("그릇 타입 (EMPTY, LESS, MORE, FULL, BURNT"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간")
                                )
                ));
    }
}