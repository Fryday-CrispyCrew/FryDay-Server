package basakan.fryday.service.report;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.report.response.CategoryReportResponse;
import basakan.fryday.controller.report.response.MonthlyReportResponse;
import basakan.fryday.domain.report.AttendanceIcon;
import basakan.fryday.domain.category.Category;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.todo.TodoRepository;
import basakan.fryday.service.report.dto.CategoryReportDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MonthlyReportReadService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final double PERCENTAGE_SCALE = 100.0;
    private static final double ROUNDING_SCALE = 10000.0;

    private final CategoryRepository categoryRepository;
    private final TodoRepository todoRepository;
    private final Executor reportAsyncExecutor;

    public MonthlyReportResponse getMonthlyReportResponse(Long userId, int year, int month) {
        LocalDate today = LocalDate.now(KOREA_ZONE);
        LocalDate requestedYearMonth = LocalDate.of(year, month, 1);
        LocalDate currentYearMonth = today.withDayOfMonth(1);

        // 미래 월 → INVALID_REPORT_PERIOD
        if (requestedYearMonth.isAfter(currentYearMonth)) {
            throw new BusinessException(ErrorCode.INVALID_REPORT_PERIOD);
        }

        // 현재 월 → 오늘까지만 집계 (비동기)
        if (requestedYearMonth.equals(currentYearMonth)) {
            return buildRealtimeReportAsync(userId, year, month, today);
        }

        // 과거 월 → 전체 월 집계 (비동기)
        return buildPastMonthReportAsync(userId, year, month);
    }

    private MonthlyReportResponse buildRealtimeReportAsync(Long userId, int year, int month, LocalDate today) {
        CompletableFuture<List<CategoryReportDto>> categoryStatsFuture =
            CompletableFuture.supplyAsync(
                () -> todoRepository.findMonthlyReportByCategoryUntilDate(userId, year, month, today),
                reportAsyncExecutor
            );

        CompletableFuture<Integer> attendanceDaysFuture =
            CompletableFuture.supplyAsync(
                () -> todoRepository.countAttendanceDaysUntilDate(userId, year, month, today),
                reportAsyncExecutor
            );

        CompletableFuture<List<Category>> categoriesFuture =
            CompletableFuture.supplyAsync(
                () -> categoryRepository.findAllByUserIdAndDeletedAtIsNullOrderByDisplayOrderAsc(userId),
                reportAsyncExecutor
            );

        CompletableFuture.allOf(categoryStatsFuture, attendanceDaysFuture, categoriesFuture).join();

        return assembleResponse(
            year, month,
            categoryStatsFuture.join(),
            attendanceDaysFuture.join(),
            categoriesFuture.join()
        );
    }

    /**
     * 과거 월 리포트 - CompletableFuture 비동기 병렬 처리
     * 3개의 독립적인 쿼리를 동시에 실행하여 성능 개선
     */
    public MonthlyReportResponse buildPastMonthReportAsync(Long userId, int year, int month) {
        CompletableFuture<List<CategoryReportDto>> categoryStatsFuture =
            CompletableFuture.supplyAsync(
                () -> todoRepository.findMonthlyReportByCategory(userId, year, month),
                reportAsyncExecutor
            );

        CompletableFuture<Integer> attendanceDaysFuture =
            CompletableFuture.supplyAsync(
                () -> todoRepository.countAttendanceDays(userId, year, month),
                reportAsyncExecutor
            );

        CompletableFuture<List<Category>> categoriesFuture =
            CompletableFuture.supplyAsync(
                () -> categoryRepository.findAllByUserIdAndDeletedAtIsNullOrderByDisplayOrderAsc(userId),
                reportAsyncExecutor
            );

        CompletableFuture.allOf(categoryStatsFuture, attendanceDaysFuture, categoriesFuture).join();

        return assembleResponse(
            year, month,
            categoryStatsFuture.join(),
            attendanceDaysFuture.join(),
            categoriesFuture.join()
        );
    }

    private MonthlyReportResponse assembleResponse(int year, int month,
                                                    List<CategoryReportDto> categoryStats,
                                                    int attendanceDays,
                                                    List<Category> activeCategories) {
        int totalTodos = categoryStats.stream()
            .mapToInt(CategoryReportDto::getTotalTodos).sum();
        int completedTodos = categoryStats.stream()
            .mapToInt(CategoryReportDto::getCompletedTodos).sum();
        int incompleteTodos = categoryStats.stream()
            .mapToInt(CategoryReportDto::getIncompleteTodos).sum();
        double achievementRate = calculateRate(completedTodos, totalTodos);

        AttendanceIcon icon = AttendanceIcon.fromAttendanceDays(attendanceDays);

        Map<Long, CategoryReportDto> statsMap = categoryStats.stream()
            .collect(Collectors.toMap(CategoryReportDto::getCategoryId, Function.identity()));

        List<CategoryReportResponse> categories = activeCategories.stream()
            .map(category -> {
                CategoryReportDto dto = statsMap.get(category.getId());
                if (dto != null) {
                    double successRate = calculateRate(dto.getCompletedTodos(), dto.getTotalTodos());
                    double failureRate = calculateRate(dto.getIncompleteTodos(), dto.getTotalTodos());
                    return CategoryReportResponse.from(dto, successRate, failureRate);
                }
                return CategoryReportResponse.empty(category);
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

    // ============================================================================
    // 기존 Sequential 버전 (주석 처리)
    // ============================================================================

    /*
    private MonthlyReportResponse buildRealtimeReport(Long userId, int year, int month, LocalDate today) {
        List<CategoryReportDto> categoryStats =
            todoRepository.findMonthlyReportByCategoryUntilDate(userId, year, month, today);
        int attendanceDays = todoRepository.countAttendanceDaysUntilDate(userId, year, month, today);

        return assembleResponseSequential(userId, year, month, categoryStats, attendanceDays);
    }

    private MonthlyReportResponse buildPastMonthReport(Long userId, int year, int month) {
        List<CategoryReportDto> categoryStats =
            todoRepository.findMonthlyReportByCategory(userId, year, month);
        int attendanceDays = todoRepository.countAttendanceDays(userId, year, month);

        return assembleResponseSequential(userId, year, month, categoryStats, attendanceDays);
    }

    private MonthlyReportResponse assembleResponseSequential(Long userId, int year, int month,
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

        Map<Long, CategoryReportDto> statsMap = categoryStats.stream()
            .collect(Collectors.toMap(CategoryReportDto::getCategoryId, Function.identity()));

        List<Category> activeCategories =
            categoryRepository.findAllByUserIdAndDeletedAtIsNullOrderByDisplayOrderAsc(userId);

        List<CategoryReportResponse> categories = activeCategories.stream()
            .map(category -> {
                CategoryReportDto dto = statsMap.get(category.getId());
                if (dto != null) {
                    double successRate = calculateRate(dto.getCompletedTodos(), dto.getTotalTodos());
                    double failureRate = calculateRate(dto.getIncompleteTodos(), dto.getTotalTodos());
                    return CategoryReportResponse.from(dto, successRate, failureRate);
                }
                return CategoryReportResponse.empty(category);
            })
            .toList();

        return MonthlyReportResponse.ofRealtime(
            year, month, totalTodos, completedTodos, incompleteTodos,
            attendanceDays, achievementRate, icon, categories);
    }
    */
}
