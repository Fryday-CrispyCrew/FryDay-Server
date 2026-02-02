package basakan.fryday.service;

import basakan.fryday.controller.todo.response.DailyResultResponse;
import basakan.fryday.domain.BowlType;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.repository.todo.TodoRepository;
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

    @Transactional(readOnly = true)
    public List<DailyResultResponse> getDailyResults(Long userId, LocalDate startDate, LocalDate endDate) {
        return startDate.datesUntil(endDate.plusDays(1))
                .map(date -> {
                    List<Todo> todos = todoRepository.findAllByUserIdAndDate(userId, date);
                    int total = todos.size();
                    int completed = (int) todos.stream().filter(Todo::isCompleted).count();
                    BowlType bowlType = BowlType.calculate(total, completed);
                    return DailyResultResponse.of(date, bowlType.getCode());
                })
                .collect(Collectors.toList());
    }
}
