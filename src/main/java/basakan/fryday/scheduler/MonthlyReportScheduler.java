package basakan.fryday.scheduler;

import basakan.fryday.repository.auth.UserJpaRepository;
import basakan.fryday.service.report.MonthlyReportGenerateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MonthlyReportScheduler {

    private final MonthlyReportGenerateService reportGenerateService;
    private final UserJpaRepository userJpaRepository;

    @Scheduled(cron = "0 0 0 1 * *", zone = "Asia/Seoul")
    public void generateMonthlyReports() {
        log.info("[MonthlyReportScheduler] 월간 리포트 생성 시작");

        LocalDate previousMonth = LocalDate.now().minusMonths(1);
        int year = previousMonth.getYear();
        int month = previousMonth.getMonthValue();

        List<Long> activeUserIds = userJpaRepository.findAllActiveUserIds();

        int successCount = 0;
        int failCount = 0;

        for (Long userId : activeUserIds) {
            try {
                reportGenerateService.generateMonthlyReport(userId, year, month);
                successCount++;
            } catch (Exception e) {
                log.error("[MonthlyReportScheduler] 리포트 생성 실패 - userId: {}, year: {}, month: {}",
                          userId, year, month, e);
                failCount++;
            }
        }

        log.info("[MonthlyReportScheduler] 월간 리포트 생성 완료 - 성공: {}, 실패: {}",
                 successCount, failCount);
    }
}
