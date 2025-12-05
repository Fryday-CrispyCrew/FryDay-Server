package basakan.fryday.service;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.todo.request.RecurrenceCreateRequest;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.todo.Recurrence;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.RecurrenceRepository;
import basakan.fryday.repository.TodoRepository;
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
    private final CategoryRepository categoryRepository;

    @Transactional
    public void createRecurrence(long userId, RecurrenceCreateRequest request) {
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        Recurrence recurrence = request.toEntity(userId);
        Recurrence savedRecurrence = recurrenceRepository.save(recurrence);

        List<Todo> todoList = new ArrayList<>();

        LocalDate currentDate = request.getStartDate();
        LocalDate limitDate = request.getEndDate();

        while (!currentDate.isAfter(limitDate)) {
            if (isMatch(request, currentDate)) {
                Long maxOrder = todoRepository.findMaxDisplayOrder(userId, currentDate);
                long nextOrder = (maxOrder == null) ? 1 : maxOrder + 1;

                Todo todo = Todo.builder()
                        .description(request.getDescription())
                        .category(category)
                        .date(currentDate)
                        .displayOrder(nextOrder)
                        .recurrenceId(savedRecurrence.getId())
                        .build();
                todoList.add(todo);
            }

            currentDate = currentDate.plusDays(1);
        }
        todoRepository.saveAll(todoList);

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
