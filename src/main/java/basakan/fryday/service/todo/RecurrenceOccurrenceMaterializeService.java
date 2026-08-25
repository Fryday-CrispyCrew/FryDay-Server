package basakan.fryday.service.todo;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.domain.todo.Recurrence;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.repository.todo.RecurrenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 반복 회차 생성을 지시하는 오케스트레이션.
 * <p>
 * 회차 1건씩의 트랜잭션 분리는 {@link RecurrenceOccurrenceWriter}가 담당한다.
 * 여기에 트랜잭션을 걸면 writer의 {@code REQUIRES_NEW}가 같은 트랜잭션에 흡수되어
 * 회차 하나의 실패가 나머지까지 되돌리므로, 이 클래스는 의도적으로 트랜잭션을 열지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecurrenceOccurrenceMaterializeService {

    private final RecurrenceRepository recurrenceRepository;
    private final RecurrenceOccurrenceWriter occurrenceWriter;

    /**
     * 특정 사용자의 특정 날짜 회차를 생성한다. 목록 조회 경로에서 호출한다.
     *
     * @param categoryId 지정하면 해당 카테고리의 반복만 대상으로 한다
     */
    public void materializeOccurrencesForDate(Long userId, LocalDate date, Long categoryId) {
        List<Recurrence> recurrences = recurrenceRepository.findByUserIdAndDateRange(userId, date);

        for (Recurrence recurrence : recurrences) {
            if (categoryId != null && recurrence.getCategoryId() != categoryId) {
                continue;
            }
            materializeQuietly(userId, recurrence.getId(), date);
        }
    }

    /**
     * 특정 사용자의 오늘 회차를 생성한다. 자정 스케줄러에서 호출한다.
     */
    public void materializeTodayOccurrences(Long userId, LocalDate today) {
        List<Recurrence> recurrences = recurrenceRepository.findByUserIdAndDateRange(userId, today);

        if (recurrences.isEmpty()) {
            return;
        }

        int createdCount = 0;
        for (Recurrence recurrence : recurrences) {
            if (materializeQuietly(userId, recurrence.getId(), today) != null) {
                createdCount++;
            }
        }

        if (createdCount > 0) {
            log.info("반복 투두 생성 완료 - userId: {}, date: {}, count: {}", userId, today, createdCount);
        }
    }

    /**
     * 회차 1건 생성. 그 날짜에 발생하지 않는 규칙은 조용히 넘어가고,
     * 예상 못 한 예외는 남은 회차 처리를 막지 않도록 로깅만 한다.
     */
    private Todo materializeQuietly(Long userId, Long recurrenceId, LocalDate date) {
        try {
            return occurrenceWriter.materializeIfAbsent(userId, recurrenceId, date);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.INVALID_INPUT_VALUE) {
                return null;
            }
            throw e;
        } catch (Exception e) {
            log.error("반복 투두 생성 실패 - recurrenceId: {}, date: {}. 스킵하고 계속 진행", recurrenceId, date, e);
            return null;
        }
    }
}
