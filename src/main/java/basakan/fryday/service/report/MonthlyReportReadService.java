package basakan.fryday.service.report;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.report.response.CategoryReportResponse;
import basakan.fryday.controller.report.response.MonthlyReportResponse;
import basakan.fryday.domain.report.AttendanceIcon;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.todo.TodoRepository;
import basakan.fryday.service.report.dto.CategoryReportDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MonthlyReportReadService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final double PERCENTAGE_SCALE = 100.0;
    private static final double ROUNDING_SCALE = 10000.0;

    private final CategoryRepository categoryRepository;
    private final TodoRepository todoRepository;

    public MonthlyReportResponse getMonthlyReportResponse(Long userId, int year, int month) {
        LocalDate today = LocalDate.now(KOREA_ZONE);
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

        // 과거 월 → 실시간 집계 (날짜 제한 없이 전체 월)
        return buildPastMonthReport(userId, year, month);
    }

    private MonthlyReportResponse buildRealtimeReport(Long userId, int year, int month, LocalDate today) {
        List<CategoryReportDto> categoryStats =
            todoRepository.findMonthlyReportByCategoryUntilDate(userId, year, month, today);
        int attendanceDays = todoRepository.countAttendanceDaysUntilDate(userId, year, month, today);

        return assembleResponse(userId, year, month, categoryStats, attendanceDays);
    }

    private MonthlyReportResponse buildPastMonthReport(Long userId, int year, int month) {
        List<CategoryReportDto> categoryStats =
            todoRepository.findMonthlyReportByCategory(userId, year, month);
        int attendanceDays = todoRepository.countAttendanceDays(userId, year, month);

        return assembleResponse(userId, year, month, categoryStats, attendanceDays);
    }

    private MonthlyReportResponse assembleResponse(Long userId, int year, int month,
                                                    List<CategoryReportDto> categoryStats,
                                                    int attendanceDays) {
        int totalTodos = categoryStats.stream()
            .mapToInt(CategoryReportDto::getTotalTodos).sum();
        int completedTodos = categoryStats.stream()
            .mapToInt(CategoryReportDto::getCompletedTodos).sum();
        int incompleteTodos = categoryStats.stream()
            .mapToInt(CategoryReportDto::getIncompleteTodos).sum();
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

    private double calculateRate(int numerator, int denominator) {
        if (denominator == 0) {
            return 0.0;
        }
        return Math.round(numerator * ROUNDING_SCALE / denominator) / PERCENTAGE_SCALE;
    }
}
