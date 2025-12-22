package basakan.fryday.scheduler;

import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.todo.Recurrence;
import basakan.fryday.domain.todo.RecurrenceType;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.todo.RecurrenceRepository;
import basakan.fryday.repository.todo.TodoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecurrenceScheduler {

    private final RecurrenceRepository recurrenceRepository;
    private final TodoRepository todoRepository;
    private final CategoryRepository categoryRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void extendInfiniteRecurrences() {
        LocalDate today = LocalDate.now();
        
        // 1달 미만 남았으면 확장
        LocalDate thresholdDate = today.plusMonths(1);

        List<Recurrence> targets = recurrenceRepository.findRecurrencesToExtend(thresholdDate);

        log.info("Found {} recurrence rules to extend", targets.size());

        for (Recurrence rule : targets) {
            extendRecurrence(rule);
        }
    }

    private void extendRecurrence(Recurrence rule) {
        LocalDate startDate = rule.getLastGeneratedDate().plusDays(1);

        LocalDate newLimitDate = startDate.plusYears(1);

        List<Todo> newTodos = new ArrayList<>();
        LocalDate current = startDate;

        Category category = categoryRepository.findById(rule.getCategoryId())
                .orElseThrow(() -> new IllegalStateException("Category not found: " + rule.getCategoryId()));

        while (!current.isAfter(newLimitDate)) {
            if (isMatch(rule, current)) {
                Long maxOrder = todoRepository.findMaxDisplayOrder(rule.getUserId(), current);
                long nextOrder = (maxOrder == null) ? 1 : maxOrder + 1;

                Todo todo = Todo.builder()
                        .description(rule.getDescription())
                        .category(category)
                        .date(current)
                        .displayOrder(nextOrder)
                        .recurrenceId(rule.getId())
                        .build();
                newTodos.add(todo);
            }
            current = current.plusDays(1);
        }

        todoRepository.saveAll(newTodos);

        // 어디까지 생성했는지 업데이트 (다음 1년 동안은 스케줄러 대상에서 제외됨)
        rule.updateLastGeneratedDate(newLimitDate);

        log.info("Extended recurrence id={}, generated {} todos until={}", 
                rule.getId(), newTodos.size(), newLimitDate);
    }

    private boolean isMatch(Recurrence recurrence, LocalDate date) {
        RecurrenceType type = recurrence.getType();
        String frequencyValuesStr = recurrence.getFrequencyValues();
        
        List<String> values = (frequencyValuesStr != null && !frequencyValuesStr.isEmpty())
                ? Arrays.asList(frequencyValuesStr.split(","))
                : null;

        switch (type) {
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
