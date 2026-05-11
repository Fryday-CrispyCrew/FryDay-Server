package basakan.fryday.service.todo;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.todo.request.InstanceEditRequest.Payload;
import basakan.fryday.domain.todo.RecurrenceScope;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.todo.EndType;
import basakan.fryday.domain.todo.Recurrence;
import basakan.fryday.domain.todo.RecurrenceType;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.todo.RecurrenceRepository;
import basakan.fryday.repository.todo.TodoAlarmRepository;
import basakan.fryday.repository.todo.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecurrenceInstanceService {

    private final TodoRepository todoRepository;
    private final RecurrenceRepository recurrenceRepository;
    private final CategoryRepository categoryRepository;
    private final TodoAlarmRepository todoAlarmRepository;
    private final RecurrenceOccurrenceCalculator occurrenceCalculator;

    @Transactional
    public void edit(long instanceId, RecurrenceScope scope, Payload payload, long userId) {
        switch (scope) {
            case THIS -> editThis(instanceId, payload, userId);
            case THIS_AND_FUTURE -> editThisAndFuture(instanceId, payload, userId);
            case ALL -> editAll(instanceId, payload, userId);
        }
    }

    @Transactional
    public void delete(long instanceId, RecurrenceScope scope, long userId) {
        switch (scope) {
            case THIS -> deleteThis(instanceId, userId);
            case THIS_AND_FUTURE -> deleteThisAndFuture(instanceId, userId);
            case ALL -> deleteAll(instanceId, userId);
        }
    }

    @Transactional
    public void cancelRecurrence(long instanceId, RecurrenceScope scope, long userId) {
        switch (scope) {
            case THIS -> cancelThis(instanceId, userId);
            case THIS_AND_FUTURE -> cancelThisAndFuture(instanceId, userId);
            case ALL -> cancelAll(instanceId, userId);
        }
    }

    // ── Edit ──────────────────────────────────────────────────────────────────

    /** spec 4.3: 해당 instance의 override 필드만 갱신 */
    private void editThis(long instanceId, Payload payload, long userId) {
        Todo instance = findActiveInstance(instanceId, userId);
        instance.applyOverride(payload.getTitle(), payload.getMemo(),
                payload.getIsAlarmEnabled(), payload.getAlarmTime());
    }

    /** spec 4.4: 기존 Master 종료 → 새 Master 생성 → T 이후 인스턴스 재생성 */
    private void editThisAndFuture(long instanceId, Payload payload, long userId) {
        Todo instance = findActiveInstance(instanceId, userId);
        LocalDate T = instance.getDate();

        Recurrence oldMaster = findMaster(instance);
        if (oldMaster.getUserId() != userId) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        // terminateAt 호출 전 원본 endType/endDate 보존 (호출 후 값이 바뀌므로)
        EndType originalEndType = oldMaster.getEndType();
        LocalDate originalEndDate = oldMaster.getEndDate();

        // STEP 1: 기존 Master 종료
        oldMaster.terminateAt(T);

        // STEP 2: 새 Master(M_new) 생성 — payload로 덮어쓴 값 적용
        // endDate: payload에 명시된 경우 우선, 없으면 기존 Master의 원본 종료 조건 인계
        LocalDate newEndDate = payload.getEndDate() != null ? payload.getEndDate()
                : (originalEndType == EndType.UNTIL ? originalEndDate : null);

        Recurrence newMaster = Recurrence.builder()
                .userId(oldMaster.getUserId())
                .categoryId(oldMaster.getCategoryId())
                .description(payload.getTitle() != null ? payload.getTitle() : oldMaster.getDescription())
                .memo(payload.getMemo() != null ? payload.getMemo() : oldMaster.getMemo())
                .type(payload.getType() != null ? payload.getType() : oldMaster.getType())
                .frequencyValues(payload.getFrequencyValues() != null
                        ? String.join(",", payload.getFrequencyValues())
                        : oldMaster.getFrequencyValues())
                .startDate(T)
                .endDate(newEndDate)
                .endType(newEndDate != null ? EndType.UNTIL : EndType.NONE)
                .notificationTime(resolveNotificationTime(payload, oldMaster.getNotificationTime()))
                .lastGeneratedDate(T)
                .build();

        Recurrence savedNewMaster = recurrenceRepository.save(newMaster);

        // STEP 3: T 이후 기존 인스턴스 일괄 soft delete (delete 전 displayOrder 보존)
        Map<LocalDate, Long> preservedOrders = todoRepository.findAllByRecurrenceIdAndDateGte(oldMaster.getId(), T)
                .stream().collect(Collectors.toMap(Todo::getDate, Todo::getDisplayOrder));
        todoRepository.bulkSoftDeleteByRecurrenceIdAndDateGte(oldMaster.getId(), T, LocalDate.now());

        // STEP 4: M_new 기준으로 T부터 새 인스턴스 배치 생성
        generateInstances(savedNewMaster, T, 365, preservedOrders);
    }

    /** spec 4.5: Master 직접 수정 — override 있는 인스턴스는 건드리지 않음 */
    private void editAll(long instanceId, Payload payload, long userId) {
        Todo instance = findActiveInstance(instanceId, userId);
        Recurrence master = findMaster(instance);

        if (master.getUserId() != userId) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        boolean hasRuleChange = payload.getType() != null || payload.getFrequencyValues() != null
                || payload.getStartDate() != null || payload.getEndDate() != null;

        if (hasRuleChange) {
            // 규칙 변경: Master 업데이트 후 오늘 이후 인스턴스 물리 삭제 → 새 규칙으로 재생성
            RecurrenceType newType = payload.getType() != null ? payload.getType() : master.getType();
            String newFrequency = payload.getFrequencyValues() != null
                    ? String.join(",", payload.getFrequencyValues()) : master.getFrequencyValues();
            LocalDate newStartDate = payload.getStartDate() != null ? payload.getStartDate() : master.getStartDate();
            LocalDate newEndDate = payload.getEndDate();

            master.updateRule(newType, newFrequency, newStartDate, newEndDate);
            master.updateContent(payload.getTitle(), payload.getMemo(),
                    resolveNotificationTime(payload, master.getNotificationTime()));

            LocalDate today = LocalDate.now();
            LocalDate generateFrom = newStartDate.isAfter(today) ? newStartDate : today;
            master.updateLastGeneratedDate(generateFrom);

            // 오늘 이후 인스턴스 물리 삭제 (과거 완료 이력 보존, delete 전 displayOrder 보존)
            Map<LocalDate, Long> preservedOrders = todoRepository.findAllByRecurrenceIdAndDateGte(master.getId(), today)
                    .stream().collect(Collectors.toMap(Todo::getDate, Todo::getDisplayOrder));
            todoRepository.hardDeleteByRecurrenceIdAndDateGte(master.getId(), today);
            generateInstances(master, generateFrom, 365, preservedOrders);
        } else {
            // 내용만 변경: Master 업데이트 + 비override 인스턴스 일괄 반영
            master.updateContent(payload.getTitle(), payload.getMemo(),
                    resolveNotificationTime(payload, master.getNotificationTime()));

            if (payload.getTitle() != null) {
                todoRepository.bulkUpdateDescriptionByRecurrenceId(master.getId(), payload.getTitle());
            }
            if (payload.getMemo() != null) {
                todoRepository.bulkUpdateMemoByRecurrenceId(master.getId(), payload.getMemo());
            }
        }
    }

    // ── Delete ─────────────────────────────────────────────────────────────────

    /** spec 5.3: 해당 인스턴스만 soft delete */
    private void deleteThis(long instanceId, long userId) {
        Todo instance = findActiveInstance(instanceId, userId);
        todoAlarmRepository.deleteByTodoId(instanceId);
        instance.delete();
    }

    /** spec 5.4: Master 종료 + T 이후 인스턴스 일괄 soft delete */
    private void deleteThisAndFuture(long instanceId, long userId) {
        Todo instance = findActiveInstance(instanceId, userId);
        LocalDate T = instance.getDate();

        Recurrence master = findMaster(instance);
        if (master.getUserId() != userId) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        master.terminateAt(T);
        todoRepository.bulkSoftDeleteByRecurrenceIdAndDateGte(master.getId(), T, LocalDate.now());
    }

    /** spec 5.5: Master soft delete + 연결된 모든 인스턴스 soft delete */
    private void deleteAll(long instanceId, long userId) {
        Todo instance = findActiveInstance(instanceId, userId);
        Recurrence master = findMaster(instance);

        if (master.getUserId() != userId) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        master.markDeleted();
        todoRepository.bulkSoftDeleteByRecurrenceId(master.getId(), LocalDate.now());
    }

    // ── Cancel ─────────────────────────────────────────────────────────────────

    /** 해당 인스턴스만 일반 Todo로 전환, 나머지 반복 유지 */
    private void cancelThis(long instanceId, long userId) {
        Todo instance = findActiveInstance(instanceId, userId);
        instance.detachFromRecurrence();
    }

    /** Master 종료(endDate=T-1) + T+1 이후 soft delete + 선택 인스턴스(T) 일반 Todo 전환 */
    private void cancelThisAndFuture(long instanceId, long userId) {
        Todo instance = findActiveInstance(instanceId, userId);
        LocalDate T = instance.getDate();

        Recurrence master = findMaster(instance);
        if (master.getUserId() != userId) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        master.terminateAt(T);
        todoRepository.bulkSoftDeleteByRecurrenceIdAndDateGte(master.getId(), T.plusDays(1), LocalDate.now());
        instance.detachFromRecurrence();
    }

    /** 선택 인스턴스 일반 Todo 전환 + 나머지 전체 soft delete + Master 물리 삭제 */
    private void cancelAll(long instanceId, long userId) {
        Todo instance = findActiveInstance(instanceId, userId);
        Long recurrenceId = instance.getRecurrenceId();

        Recurrence recurrence = recurrenceRepository.findById(recurrenceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        if (recurrence.getUserId() != userId) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        List<Todo> todos = todoRepository.findAllByRecurrenceId(recurrenceId);
        for (Todo todo : todos) {
            if (todo.getId().equals(instance.getId())) {
                todo.detachFromRecurrence();
            } else {
                todo.delete();
            }
        }

        recurrenceRepository.delete(recurrence);
    }

    // ── generateInstances ──────────────────────────────────────────────────────

    /**
     * spec 6: master 기준으로 startDate부터 limitDays 범위 내 인스턴스 배치 생성.
     * 이미 존재하는 날짜(삭제 포함)는 SKIP.
     */
    public List<Todo> generateInstances(Recurrence master, LocalDate startDate, int limitDays) {
        return generateInstances(master, startDate, limitDays, Map.of());
    }

    public List<Todo> generateInstances(Recurrence master, LocalDate startDate, int limitDays,
                                        Map<LocalDate, Long> preservedOrders) {
        LocalDate endLimit = startDate.plusDays(limitDays);
        LocalDate toDate = (master.getEndType() == EndType.UNTIL && master.getEndDate() != null
                && master.getEndDate().isBefore(endLimit))
                ? master.getEndDate()
                : endLimit;

        Category category = categoryRepository.findById(master.getCategoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        List<LocalDate> occurrenceDates = occurrenceCalculator.calculateOccurrences(master, startDate, toDate);

        return occurrenceDates.stream()
                .filter(date -> !todoRepository.existsByRecurrenceIdAndDate(master.getId(), date))
                .map(date -> {
                    long displayOrder = preservedOrders.containsKey(date)
                            ? preservedOrders.get(date)
                            : Optional.ofNullable(todoRepository.findMaxDisplayOrder(master.getUserId(), date))
                                    .map(max -> max + 1).orElse(1L);

                    Todo todo = Todo.builder()
                            .description(master.getDescription())
                            .category(category)
                            .date(date)
                            .displayOrder(displayOrder)
                            .recurrenceId(master.getId())
                            .memo(master.getMemo())
                            .build();

                    return todoRepository.save(todo);
                })
                .toList();
    }

    // ── 공통 헬퍼 ──────────────────────────────────────────────────────────────

    /** isAlarmEnabled=false이면 null(알람 OFF), 그 외에는 alarmTime 또는 기존 값 유지 */
    private LocalTime resolveNotificationTime(Payload payload, LocalTime existing) {
        if (Boolean.FALSE.equals(payload.getIsAlarmEnabled())) return null;
        return payload.getAlarmTime() != null ? payload.getAlarmTime() : existing;
    }

    private Todo findActiveInstance(long instanceId, long userId) {
        Todo instance = todoRepository.findById(instanceId)
                .filter(t -> !t.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));

        if (!instance.getCategory().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        if (instance.getRecurrenceId() == null) {
            throw new BusinessException(ErrorCode.NOT_RECURRING_TODO);
        }

        return instance;
    }

    private Recurrence findMaster(Todo instance) {
        return recurrenceRepository.findById(instance.getRecurrenceId())
                .filter(r -> !r.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));
    }
}
