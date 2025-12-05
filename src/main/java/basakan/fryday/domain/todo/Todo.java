package basakan.fryday.domain.todo;

import basakan.fryday.domain.BaseEntity;
import basakan.fryday.domain.category.Category;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

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

    private long recurrenceId;

    public enum Status {
        IN_PROGRESS, // 미완료 (체크 안 됨)
        COMPLETED    // 완료 (체크 됨)
    }

    @Builder
    public Todo(String description, Category category, LocalDate date, Long displayOrder, long recurrenceId) {
        this.description = description;
        this.status = Status.IN_PROGRESS;
        this.category = category;
        this.date = (date != null) ? date : LocalDate.now();
        this.displayOrder = displayOrder != null ? displayOrder : 0L;
        this.recurrenceId = recurrenceId;
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

}
