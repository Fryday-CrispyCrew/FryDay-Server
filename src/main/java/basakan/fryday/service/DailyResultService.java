package basakan.fryday.service;

import basakan.fryday.controller.todo.response.DailyResultResponse;
import basakan.fryday.domain.DailyResult;
import basakan.fryday.domain.BowlType;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.repository.DailyResultRepository;
import basakan.fryday.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DailyResultService {

    private final TodoRepository todoRepository;
    private final DailyResultRepository dailyResultRepository;

    @Transactional
    public void recordDailyResult(Long userId, LocalDate date) {
        if (dailyResultRepository.findByUserIdAndDate(userId, date).isPresent()) {
            return;
        }

        List<Todo> todos = todoRepository.findAllByUserIdAndDate(userId, date); // 추후 개선

        int total = todos.size();
        int completed = (int) todos.stream()
                .filter(Todo::isCompleted)
                .count();

        BowlType bowlType = BowlType.calculate(total, completed);

        DailyResult result = DailyResult.builder()
                .userId(userId)
                .date(date)
                .bowlType(bowlType)
                .build();

        dailyResultRepository.save(result);
    }

    @Transactional(readOnly = true)
    public List<DailyResultResponse> getDailyResults(Long userId, LocalDate startDate, LocalDate endDate) {
        List<DailyResult> results = dailyResultRepository.findAllByUserIdAndDateBetween(userId, startDate, endDate);

        return results.stream()
                .map(DailyResultResponse::from)
                .collect(Collectors.toList());
    }
}
