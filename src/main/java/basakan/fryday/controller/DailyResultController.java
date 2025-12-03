package basakan.fryday.controller;

import basakan.fryday.common.response.ApiResponse;
import basakan.fryday.controller.todo.response.DailyResultResponse;
import basakan.fryday.service.DailyResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/daily-results")
public class DailyResultController {

    private final DailyResultService dailyResultService;

    @GetMapping
    public ApiResponse<List<DailyResultResponse>> getDailyResults(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate
    ) {
        Long currentUserId = 1L;

        List<DailyResultResponse> response = dailyResultService.getDailyResults(currentUserId, startDate, endDate);
        return ApiResponse.success(response);
    }

}
