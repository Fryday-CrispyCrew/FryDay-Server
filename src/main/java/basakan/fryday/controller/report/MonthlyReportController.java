package basakan.fryday.controller.report;

import basakan.fryday.controller.report.response.MonthlyReportResponse;
import basakan.fryday.service.report.MonthlyReportReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class MonthlyReportController {

    private final MonthlyReportReadService monthlyReportReadService;

    @GetMapping("/monthly")
    public ResponseEntity<MonthlyReportResponse> getMonthlyReport(
            @RequestParam int year,
            @RequestParam int month,
            @AuthenticationPrincipal Long userId) {

        MonthlyReportResponse response = monthlyReportReadService.getMonthlyReportResponse(userId, year, month);

        return ResponseEntity.ok(response);
    }
}
