package basakan.fryday.service.report;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.domain.report.MonthlyReport;
import basakan.fryday.repository.report.MonthlyReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MonthlyReportReadService {

    private final MonthlyReportRepository monthlyReportRepository;

    public MonthlyReport getMonthlyReport(Long userId, int year, int month) {
        LocalDate now = LocalDate.now();
        LocalDate requestedYearMonth = LocalDate.of(year, month, 1);
        LocalDate currentYearMonth = now.withDayOfMonth(1);

        if (!requestedYearMonth.isBefore(currentYearMonth)) {
            throw new BusinessException(ErrorCode.INVALID_REPORT_PERIOD);
        }

        return monthlyReportRepository
            .findByUserIdAndYearAndMonth(userId, year, month)
            .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
    }
}
