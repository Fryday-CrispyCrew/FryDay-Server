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
@Table(name = "recurrence_occurrence_state", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"recurrence_id", "occurrence_date"})
})
public class RecurrenceOccurrenceState extends BaseEntity {

    @Column(name = "recurrence_id", nullable = false)
    private Long recurrenceId;

    @Column(name = "occurrence_date", nullable = false)
    private LocalDate occurrenceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    public enum Status {
        IN_PROGRESS,  // 미완료
        COMPLETED     // 완료
    }

    @Builder
    public RecurrenceOccurrenceState(Long recurrenceId, LocalDate occurrenceDate, Status status) {
        this.recurrenceId = recurrenceId;
        this.occurrenceDate = occurrenceDate;
        this.status = status;
    }

    public void updateStatus(Status status) {
        this.status = status;
    }
}