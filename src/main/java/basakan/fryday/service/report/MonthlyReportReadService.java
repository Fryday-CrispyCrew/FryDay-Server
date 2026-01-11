package basakan.fryday.service.report;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.report.response.CategoryReportResponse;
import basakan.fryday.controller.report.response.MonthlyReportResponse;
import basakan.fryday.domain.report.MonthlyReport;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.report.MonthlyReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MonthlyReportReadService {

    private final MonthlyReportRepository monthlyReportRepository;
    private final CategoryRepository categoryRepository;

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

    public MonthlyReportResponse getMonthlyReportResponse(Long userId, int year, int month) {
        MonthlyReport report = getMonthlyReport(userId, year, month);

        List<CategoryReportResponse> filteredCategories = report.getCategories().stream()
            .filter(category -> categoryRepository.findById(category.getCategoryId())
                .map(c -> c.getDeletedAt() == null)
                .orElse(false))
            .map(CategoryReportResponse::from)
            .toList();

        return MonthlyReportResponse.from(report, filteredCategories);
    }
}
