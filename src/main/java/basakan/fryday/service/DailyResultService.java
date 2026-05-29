package basakan.fryday.service;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.todo.response.DailyResultResponse;
import basakan.fryday.domain.BowlType;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.repository.todo.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DailyResultService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final TodoRepository todoRepository;

    @Transactional(readOnly = true)
    public List<DailyResultResponse> getDailyResults(Long userId, LocalDate startDate, LocalDate endDate) {

        if (startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "시작일은 종료일보다 이후일 수 없습니다.");
        }

        LocalDate today = LocalDate.now(KOREA_ZONE);

        return startDate.datesUntil(endDate.plusDays(1))
                .map(date -> {
                    List<Todo> todos = todoRepository.findAllByUserIdAndDate(userId, date);
                    int total = todos.size();
                    int completed = (int) todos.stream().filter(Todo::isCompleted).count();
                    BowlType bowlType = BowlType.calculate(total, completed, date, today);
                    return DailyResultResponse.of(date, bowlType != null ? bowlType.getCode() : null);
                })
                .collect(Collectors.toList());
    }
}
