package basakan.fryday.service.todo;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.todo.request.RecurrenceCreateRequest;
import basakan.fryday.controller.todo.response.TodoResponse;
import basakan.fryday.domain.todo.Recurrence;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.repository.todo.RecurrenceRepository;
import basakan.fryday.repository.todo.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecurrenceService {

    private final RecurrenceRepository recurrenceRepository;
    private final TodoRepository todoRepository;

    @Transactional
    public TodoResponse createRecurrence(Long userId, RecurrenceCreateRequest request) {
        Todo originalTodo = todoRepository.findById(request.getTodoId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        originalTodo.updateDate(request.getStartDate());

        LocalDate today = LocalDate.now();
        LocalDate generationStartDate = request.getStartDate().isBefore(today) ? today : request.getStartDate();
        
        // 종료일이 없으면 오늘부터 +1년까지만 생성
        LocalDate limitDate = (request.getEndDate() != null)
                ? request.getEndDate()
                : today.plusYears(1);

        Recurrence recurrence = Recurrence.builder()
                .userId(userId)
                .categoryId(originalTodo.getCategory().getId())
                .description(originalTodo.getDescription())
                .type(request.getType())
                .frequencyValues(request.getFrequencyValues() != null 
                        ? String.join(",", request.getFrequencyValues()) 
                        : null)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate()) // null이면 무한 반복
                .notificationTime(request.getNotificationTime())
                .lastGeneratedDate(limitDate)
                .build();

        Recurrence savedRecurrence = recurrenceRepository.save(recurrence);

        originalTodo.setRecurrenceId(savedRecurrence.getId());

        List<Todo> todoList = new ArrayList<>();

        // 시작일부터 limitDate까지 투두 생성
        LocalDate currentDate = generationStartDate.plusDays(1);

        while (!currentDate.isAfter(limitDate)) {
            if (isMatch(request, currentDate)) {
                Long maxOrder = todoRepository.findMaxDisplayOrder(userId, currentDate);
                long nextOrder = (maxOrder == null) ? 1 : maxOrder + 1;

                Todo todo = Todo.builder()
                        .description(originalTodo.getDescription())
                        .category(originalTodo.getCategory())
                        .date(currentDate)
                        .displayOrder(nextOrder)
                        .recurrenceId(savedRecurrence.getId())
                        .build();
                todoList.add(todo);
            }
            currentDate = currentDate.plusDays(1);
        }
        todoRepository.saveAll(todoList);

        return TodoResponse.from(originalTodo);
    }

    private boolean isMatch(RecurrenceCreateRequest request, LocalDate date) {
        List<String> values = request.getFrequencyValues();

        switch (request.getType()) {
            case DAILY:
                return true;

            case WEEKLY:
                if (values == null || values.isEmpty()) {
                    return false;
                }
                return values.contains(date.getDayOfWeek().name());

            case MONTHLY:
                if (values == null || values.isEmpty()) {
                    return false;
                }
                return values.contains(String.valueOf(date.getDayOfMonth()));

            case YEARLY:
                if (values == null || values.isEmpty()) {
                    return false;
                }
                String monthDay = date.format(DateTimeFormatter.ofPattern("MM-dd"));
                return values.contains(monthDay);

            default:
                return false;
        }
    }
}
