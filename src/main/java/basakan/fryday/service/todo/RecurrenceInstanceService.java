package basakan.fryday.service.todo;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.todo.request.InstanceDeleteRequest.DeleteScope;
import basakan.fryday.controller.todo.request.InstanceEditRequest.EditScope;
import basakan.fryday.controller.todo.request.InstanceEditRequest.Payload;
import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.todo.EndType;
import basakan.fryday.domain.todo.Recurrence;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.repository.CategoryRepository;
import basakan.fryday.repository.todo.RecurrenceRepository;
import basakan.fryday.repository.todo.TodoAlarmRepository;
import basakan.fryday.repository.todo.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecurrenceInstanceService {

    private final TodoRepository todoRepository;
    private final RecurrenceRepository recurrenceRepository;
    private final CategoryRepository categoryRepository;
    private final TodoAlarmRepository todoAlarmRepository;
    private final RecurrenceOccurrenceCalculator occurrenceCalculator;

    @Transactional
    public void edit(long instanceId, EditScope scope, Payload payload, long userId) {
        switch (scope) {
            case THIS -> editThis(instanceId, payload, userId);
            case THIS_AND_FUTURE -> editThisAndFuture(instanceId, payload, userId);
            case ALL -> editAll(instanceId, payload, userId);
        }
    }

    @Transactional
    public void delete(long instanceId, DeleteScope scope, long userId) {
        switch (scope) {
            case THIS -> deleteThis(instanceId, userId);
            case THIS_AND_FUTURE -> deleteThisAndFuture(instanceId, userId);
            case ALL -> deleteAll(instanceId, userId);
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
        Recurrence newMaster = Recurrence.builder()
                .userId(oldMaster.getUserId())
                .categoryId(oldMaster.getCategoryId())
                .description(payload.getTitle() != null ? payload.getTitle() : oldMaster.getDescription())
                .memo(payload.getMemo() != null ? payload.getMemo() : oldMaster.getMemo())
                .type(oldMaster.getType())
                .frequencyValues(oldMaster.getFrequencyValues())
                .startDate(T)
                .endDate(originalEndType == EndType.UNTIL ? originalEndDate : null)
                .endType(originalEndType == EndType.UNTIL ? EndType.UNTIL : EndType.NONE)
                .endCount(oldMaster.getEndCount())
                .notificationTime(payload.getAlarmTime() != null ? payload.getAlarmTime() : oldMaster.getNotificationTime())
                .isAlarmEnabled(payload.getIsAlarmEnabled() != null ? payload.getIsAlarmEnabled() : oldMaster.isAlarmEnabled())
                .lastGeneratedDate(T)
                .build();

        Recurrence savedNewMaster = recurrenceRepository.save(newMaster);

        // STEP 3: T 이후 기존 인스턴스 일괄 soft delete
        todoRepository.bulkSoftDeleteByRecurrenceIdAndDateGte(oldMaster.getId(), T, LocalDate.now());

        // STEP 4: M_new 기준으로 T부터 새 인스턴스 배치 생성
        generateInstances(savedNewMaster, T, 365);
    }

    /** spec 4.5: Master 직접 수정 — override 있는 인스턴스는 건드리지 않음 */
    private void editAll(long instanceId, Payload payload, long userId) {
        Todo instance = findActiveInstance(instanceId, userId);
        Recurrence master = findMaster(instance);

        if (master.getUserId() != userId) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }

        master.updateContent(
                payload.getTitle(),
                payload.getMemo(),
                payload.getAlarmTime() != null ? payload.getAlarmTime() : master.getNotificationTime(),
                payload.getIsAlarmEnabled() != null ? payload.getIsAlarmEnabled() : master.isAlarmEnabled()
        );

        // 이미 materialized된 비override 인스턴스에도 변경 내용 반영
        if (payload.getTitle() != null) {
            todoRepository.bulkUpdateDescriptionByRecurrenceId(master.getId(), payload.getTitle());
        }
        if (payload.getMemo() != null) {
            todoRepository.bulkUpdateMemoByRecurrenceId(master.getId(), payload.getMemo());
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

    // ── generateInstances ──────────────────────────────────────────────────────

    /**
     * spec 6: master 기준으로 startDate부터 limitDays 범위 내 인스턴스 배치 생성.
     * 이미 존재하는 날짜(삭제 포함)는 SKIP.
     */
    public List<Todo> generateInstances(Recurrence master, LocalDate startDate, int limitDays) {
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
                    Long maxOrder = todoRepository.findMaxDisplayOrder(master.getUserId(), date);
                    long displayOrder = (maxOrder == null) ? 1 : maxOrder + 1;

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
