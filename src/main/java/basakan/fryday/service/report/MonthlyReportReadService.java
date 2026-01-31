package basakan.fryday.service.report;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.report.response.CategoryReportResponse;
import basakan.fryday.controller.report.response.MonthlyReportResponse;
import basakan.fryday.domain.report.AttendanceIcon;
import basakan.fryday.domain.report.MonthlyReport;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.report.MonthlyReportRepository;
import basakan.fryday.repository.todo.TodoRepository;
import basakan.fryday.service.report.dto.CategoryReportDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MonthlyReportReadService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final double PERCENTAGE_SCALE = 100.0;
    private static final double ROUNDING_SCALE = 10000.0;
    private static final int GRACE_PERIOD_MINUTES = 10;

    private final MonthlyReportRepository monthlyReportRepository;
    private final CategoryRepository categoryRepository;
    private final TodoRepository todoRepository;

    public MonthlyReportResponse getMonthlyReportResponse(Long userId, int year, int month) {
        LocalDateTime now = LocalDateTime.now(KOREA_ZONE);
        LocalDate today = now.toLocalDate();
        LocalDate requestedYearMonth = LocalDate.of(year, month, 1);
        LocalDate currentYearMonth = today.withDayOfMonth(1);

        // 미래 월 → INVALID_REPORT_PERIOD
        if (requestedYearMonth.isAfter(currentYearMonth)) {
            throw new BusinessException(ErrorCode.INVALID_REPORT_PERIOD);
        }

        // 현재 월 → Todo 실시간 집계 (오늘까지만)
        if (requestedYearMonth.equals(currentYearMonth)) {
            return buildRealtimeReport(userId, year, month, today);
        }

        // 과거 월 → 스냅샷 조회
        return monthlyReportRepository
            .findByUserIdAndYearAndMonth(userId, year, month)
            .map(this::buildSnapshotResponse)
            .orElseGet(() -> {
                // 매월 1일 00:00~00:10 유예 시간
                if (today.getDayOfMonth() == 1 && now.getHour() == 0 && now.getMinute() < GRACE_PERIOD_MINUTES) {
                    throw new BusinessException(ErrorCode.REPORT_GENERATING);
                }
                throw new BusinessException(ErrorCode.REPORT_NOT_FOUND);
            });
    }

    private MonthlyReportResponse buildRealtimeReport(Long userId, int year, int month, LocalDate today) {
        List<CategoryReportDto> categoryStats =
            todoRepository.findMonthlyReportByCategoryUntilDate(userId, year, month, today);

        int totalTodos = categoryStats.stream()
            .mapToInt(CategoryReportDto::getTotalTodos).sum();
        int completedTodos = categoryStats.stream()
            .mapToInt(CategoryReportDto::getCompletedTodos).sum();
        int incompleteTodos = totalTodos - completedTodos;
        int attendanceDays = todoRepository.countAttendanceDaysUntilDate(userId, year, month, today);
        double achievementRate = calculateRate(completedTodos, totalTodos);

        AttendanceIcon icon = AttendanceIcon.fromAttendanceDays(attendanceDays);

        List<CategoryReportResponse> categories = categoryStats.stream()
            .filter(dto -> categoryRepository.findById(dto.getCategoryId())
                .map(c -> c.getDeletedAt() == null)
                .orElse(false))
            .map(dto -> {
                double successRate = calculateRate(dto.getCompletedTodos(), dto.getTotalTodos());
                double failureRate = calculateRate(dto.getIncompleteTodos(), dto.getTotalTodos());
                return CategoryReportResponse.from(dto, successRate, failureRate);
            })
            .toList();

        return MonthlyReportResponse.ofRealtime(
            year, month, totalTodos, completedTodos, incompleteTodos,
            attendanceDays, achievementRate, icon, categories);
    }

    private MonthlyReportResponse buildSnapshotResponse(MonthlyReport report) {
        List<CategoryReportResponse> filteredCategories = report.getCategories().stream()
            .filter(category -> categoryRepository.findById(category.getCategoryId())
                .map(c -> c.getDeletedAt() == null)
                .orElse(false))
            .map(CategoryReportResponse::from)
            .toList();

        return MonthlyReportResponse.from(report, filteredCategories);
    }

    private double calculateRate(int numerator, int denominator) {
        if (denominator == 0) {
            return 0.0;
        }
        return Math.round(numerator * ROUNDING_SCALE / denominator) / PERCENTAGE_SCALE;
    }
}
