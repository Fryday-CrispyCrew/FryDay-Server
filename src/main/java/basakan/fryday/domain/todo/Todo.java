package basakan.fryday.domain.todo;

import basakan.fryday.domain.BaseEntity;
import basakan.fryday.domain.category.Category;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Todo extends BaseEntity {

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(length = 300)
    private String memo;

    @Column(nullable = false)
    private LocalDate date;

    private LocalDate deletedAt;

    @Column(nullable = false)
    private Long displayOrder;

    @Column(name = "recurrence_id")
    private Long recurrenceId;

    @OneToOne(mappedBy = "todo", cascade = CascadeType.ALL, orphanRemoval = true)
    private TodoAlarm todoAlarm;

    // 반복 인스턴스 override 필드 — non-recurring Todo에는 null
    private String overrideTitle;

    @Column(length = 300)
    private String overrideMemo;

    private Boolean overrideIsAlarm;

    private LocalTime overrideAlarmTime;

    @Column(nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean isOverridden;

    public enum Status {
        IN_PROGRESS, // 미완료 (체크 안 됨)
        COMPLETED    // 완료 (체크 됨)
    }

    @Builder
    public Todo(String description, Category category, LocalDate date, Long displayOrder, Long recurrenceId, String memo) {
        this.description = description;
        this.status = Status.IN_PROGRESS;
        this.category = category;
        this.date = (date != null) ? date : LocalDate.now();
        this.displayOrder = displayOrder != null ? displayOrder : 0L;
        // recurrenceId가 0이면 null로 변환 (반복 없는 투두는 null)
        this.recurrenceId = (recurrenceId != null && recurrenceId == 0) ? null : recurrenceId;
        this.memo = memo;
    }

    public void toggleCompletion() {
        if (this.status == Status.COMPLETED) {
            this.status = Status.IN_PROGRESS;
        } else {
            this.status = Status.COMPLETED;
        }
    }

    public boolean isCompleted() {
        return this.status == Status.COMPLETED;
    }

    public void updateMemo(String memo) {
        this.memo = memo;
    }

    public void delete() {
        this.deletedAt = LocalDate.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }


    public void updateDate(LocalDate date) {
        this.date = date;
    }

    public void updateDisplayOrder(Long displayOrder) {
        this.displayOrder = displayOrder;
    }

    public void setRecurrenceId(Long recurrenceId) {
        this.recurrenceId = recurrenceId;
    }

    public void updateCategory(Category category) {
        this.category = category;
    }

    public void updateDescription(String description) {
        this.description = description;
    }

    public void applyOverride(String title, String memo, Boolean isAlarm, LocalTime alarmTime) {
        if (title != null) this.overrideTitle = title;
        if (memo != null) this.overrideMemo = memo;
        if (isAlarm != null) this.overrideIsAlarm = isAlarm;
        if (alarmTime != null) this.overrideAlarmTime = alarmTime;
        this.isOverridden = true;
    }

    /** override 값을 base 필드에 이관하고 반복 연결을 끊어 독립 Todo로 전환 */
    public void detachFromRecurrence() {
        if (this.overrideTitle != null) this.description = this.overrideTitle;
        if (this.overrideMemo != null) this.memo = this.overrideMemo;

        this.overrideTitle = null;
        this.overrideMemo = null;
        this.overrideIsAlarm = null;
        this.overrideAlarmTime = null;
        this.isOverridden = false;

        this.recurrenceId = null;
    }

}
