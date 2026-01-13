package basakan.fryday.domain.todo;

import basakan.fryday.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "recurrence_exception", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"recurrence_id", "occurrence_date"})
})
public class RecurrenceException extends BaseEntity {

    @Column(name = "recurrence_id", nullable = false)
    private Long recurrenceId;

    @Column(name = "occurrence_date", nullable = false)
    private LocalDate occurrenceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExceptionType type;

    @Column(name = "detached_todo_id")
    private Long detachedTodoId;

    public enum ExceptionType {
        CANCELLED,  // 제외(삭제)
        DETACHED    // 분리(반복에서 제외하고 단건 todo로 변환)
    }

    @Builder
    public RecurrenceException(Long recurrenceId, LocalDate occurrenceDate, ExceptionType type, Long detachedTodoId) {
        this.recurrenceId = recurrenceId;
        this.occurrenceDate = occurrenceDate;
        this.type = type;
        this.detachedTodoId = detachedTodoId;
    }
}