package basakan.fryday.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    public enum Status {
        IN_PROGRESS, // 튀기는 중(기본 상태)
        COMPLETED,   // 튀김(완료)
        FAILED       // 미완료(탐)
    }

    @Builder
    public Todo(String description, Category category, LocalDate date) {
        this.description = description;
        this.status = Status.IN_PROGRESS;
        this.category = category;
        this.date = (date != null) ? date : LocalDate.now();
    }

    public void toggleCompletion() {
        if (this.status == Status.COMPLETED) {
            this.status = Status.IN_PROGRESS;
        } else {
            this.status = Status.COMPLETED;
        }
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

    public boolean isFailed() {
        return this.status == Status.FAILED;
    }

    public void updateDate(LocalDate date) {
        this.date = date;
    }

}
