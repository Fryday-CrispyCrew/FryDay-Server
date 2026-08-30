package basakan.fryday.service.todo;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.todo.request.InstanceEditRequest.Payload;
import basakan.fryday.domain.todo.RecurrenceScope;
import basakan.fryday.domain.todo.EndType;
import basakan.fryday.domain.todo.Recurrence;
import basakan.fryday.domain.todo.RecurrenceType;
import basakan.fryday.domain.todo.Todo;
import basakan.fryday.repository.todo.RecurrenceRepository;
import basakan.fryday.repository.todo.TodoAlarmRepository;
import basakan.fryday.repository.todo.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecurrenceInstanceService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final TodoRepository todoRepository;
    private final RecurrenceRepository recurrenceRepository;
    private final TodoAlarmRepository todoAlarmRepository;
    private final RecurrenceOccurrenceCalculator occurrenceCalculator;
    private final TodoAlarmSynchronizer alarmSynchronizer;

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

    /** spec 4.2: 해당 instance의 override 필드 갱신 + 개별 알림을 발송 행에 반영 */
    private void editThis(long instanceId, Payload payload, long userId) {
        Todo instance = findActiveInstance(instanceId, userId);
        validateAlarmOverride(payload, instance);

        instance.applyOverride(payload.getTitle(), payload.getMemo(),
                payload.getIsAlarmEnabled(), payload.getAlarmTime());

        if (payload.getIsAlarmEnabled() != null) {
            // override가 확정된 상태이므로 Master 시각은 판정에 쓰이지 않는다
            alarmSynchronizer.sync(instance, userId, instance.resolveEffectiveAlarmTime(null));
        }
    }

    /** 개별 알림 설정 시 시각 필수, 지난 시각 거부 */
    private void validateAlarmOverride(Payload payload, Todo instance) {
        if (!Boolean.TRUE.equals(payload.getIsAlarmEnabled())) {
            return;
        }
        if (payload.getAlarmTime() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        LocalDateTime notifyAt = LocalDateTime.of(instance.getDate(), payload.getAlarmTime());
        if (notifyAt.isBefore(LocalDateTime.now(KOREA_ZONE))) {
            throw new BusinessException(ErrorCode.ALARM_TIME_IN_PAST);
        }
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

        // STEP 3: T 이후 인스턴스를 새 규칙에 비춰 재배치한다.
        //         지우고 다시 만들면 displayOrder·알람·완료 상태가 날아가므로,
        //         새 규칙에 여전히 해당하는 회차는 그대로 두고 소속만 옮긴다.
        realignInstances(oldMaster.getId(), savedNewMaster, T, payload, userId);
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
            LocalDate realignFrom = newStartDate.isAfter(today) ? newStartDate : today;

            // 오늘 이후 인스턴스를 새 규칙에 비춰 재배치한다 (과거 완료 이력은 보존).
            // Master가 그대로이므로 소속을 옮길 필요는 없고, 규칙에서 벗어난 회차만 정리한다.
            realignInstances(master.getId(), master, realignFrom, payload, userId);
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
            if (hasAlarmChange(payload)) {
                propagateAlarmsToInheritingInstances(master, userId, LocalDate.now());
            }
        }
    }

    /** Master 알림 변경을 오늘 이후의 상속(비override) 회차 발송 행에 전파한다. */
    private void propagateAlarmsToInheritingInstances(Recurrence master, long userId, LocalDate fromDate) {
        List<Todo> instances = todoRepository.findAllByRecurrenceIdAndDateGte(master.getId(), fromDate);
        for (Todo instance : instances) {
            if (instance.inheritsAlarm()) {
                alarmSynchronizer.sync(instance, userId, master.getNotificationTime());
            }
        }
    }

    private boolean hasAlarmChange(Payload payload) {
        return payload.getIsAlarmEnabled() != null || payload.getAlarmTime() != null;
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

    /**
     * 해당 인스턴스만 일반 Todo로 전환, 나머지 반복 유지.
     */
    private void cancelThis(long instanceId, long userId) {
        Todo instance = findActiveInstance(instanceId, userId);

        String finalDescription = instance.getOverrideTitle() != null
                ? instance.getOverrideTitle() : instance.getDescription();
        String finalMemo = instance.getOverrideMemo() != null
                ? instance.getOverrideMemo() : instance.getMemo();
        boolean wasCompleted = instance.isCompleted();

        instance.delete();

        Todo standalone = Todo.builder()
                .description(finalDescription)
                .category(instance.getCategory())
                .date(instance.getDate())
                .displayOrder(instance.getDisplayOrder())
                .memo(finalMemo)
                .build();
        Todo saved = todoRepository.save(standalone);

        if (wasCompleted) {
            saved.toggleCompletion();
        }

        todoAlarmRepository.findByTodoId(instanceId)
                .ifPresent(alarm -> alarm.reassignTo(saved));
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
        // clearAutomatically = true로 영속성 컨텍스트가 초기화되므로 이후 재조회
        todoRepository.bulkSoftDeleteByRecurrenceIdAndDateGte(master.getId(), T.plusDays(1), LocalDate.now());

        todoRepository.findById(instanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND))
                .detachFromRecurrence();
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

    // ── realignInstances ───────────────────────────────────────────────────────

    /**
     * fromDate 이후의 기존 인스턴스를 새 규칙에 비춰 재배치한다.
     * 지우고 다시 만드는 대신 차이만 반영한다. 새 규칙에도 해당하는 회차는 행을 그대로 두므로
     * displayOrder·알람·override·완료 상태가 자연히 유지되고, 규칙에서 벗어난 회차만 정리된다.
     * 새로 규칙에 들어온 날짜는 조회 시점에 생성된다(지연 생성).
     *
     * @param sourceRecurrenceId 재배치 대상 인스턴스가 현재 매달려 있는 Master
     * @param newMaster          새 규칙. sourceRecurrenceId와 다르면 살아남은 회차의 소속을 옮긴다
     */
    private void realignInstances(Long sourceRecurrenceId, Recurrence newMaster,
                                  LocalDate fromDate, Payload payload, long userId) {
        List<Todo> candidates = todoRepository.findAllByRecurrenceIdAndDateGte(sourceRecurrenceId, fromDate);

        List<Long> keptIds = new ArrayList<>();
        List<Long> droppedIds = new ArrayList<>();

        for (Todo instance : candidates) {
            if (matchesRule(newMaster, instance.getDate())) {
                keptIds.add(instance.getId());
                applyContentChange(instance, payload);
                if (hasAlarmChange(payload) && instance.inheritsAlarm()) {
                    alarmSynchronizer.sync(instance, userId, newMaster.getNotificationTime());
                }
            } else {
                droppedIds.add(instance.getId());
            }
        }

        if (!droppedIds.isEmpty()) {
            todoRepository.softDeleteByIds(droppedIds, LocalDate.now());
        }

        if (!keptIds.isEmpty() && !newMaster.getId().equals(sourceRecurrenceId)) {
            todoRepository.reassignRecurrenceId(keptIds, newMaster.getId());
        }
    }

    /** 새 규칙의 기간과 주기 양쪽을 만족하는 날짜인지 판정한다. */
    private boolean matchesRule(Recurrence master, LocalDate date) {
        if (date.isBefore(master.getStartDate())) {
            return false;
        }
        if (master.getEndType() == EndType.UNTIL && master.getEndDate() != null
                && date.isAfter(master.getEndDate())) {
            return false;
        }
        return occurrenceCalculator.isMatch(master, date);
    }

    /**
     * 살아남은 회차에 내용 변경을 반영한다.
     * 사용자가 개별 수정한(override) 회차는 건드리지 않는다.
     */
    private void applyContentChange(Todo instance, Payload payload) {
        if (instance.isOverridden()) {
            return;
        }
        if (payload.getTitle() != null) {
            instance.updateDescription(payload.getTitle());
        }
        if (payload.getMemo() != null) {
            instance.updateMemo(payload.getMemo());
        }
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
