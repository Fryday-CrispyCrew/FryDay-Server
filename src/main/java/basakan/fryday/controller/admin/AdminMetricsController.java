package basakan.fryday.controller.admin;

import basakan.fryday.common.response.ApiResponse;
import basakan.fryday.controller.admin.response.DailyMetricsResponse;
import basakan.fryday.controller.admin.response.WeeklyMetricsResponse;
import basakan.fryday.service.admin.AdminMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/metrics")
@RequiredArgsConstructor
public class AdminMetricsController {

    private final AdminMetricsService adminMetricsService;

    @GetMapping("/daily")
    public ApiResponse<DailyMetricsResponse> getDailyMetrics(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(defaultValue = "true") boolean excludeInternal
    ) {
        return ApiResponse.success(adminMetricsService.getDailyMetrics(from, to, excludeInternal));
    }

    @GetMapping("/weekly")
    public ApiResponse<WeeklyMetricsResponse> getWeeklyMetrics(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(defaultValue = "true") boolean excludeInternal
    ) {
        return ApiResponse.success(adminMetricsService.getWeeklyMetrics(from, to, excludeInternal));
    }
}
