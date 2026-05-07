package basakan.fryday.domain.todo;

import basakan.fryday.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recurrence extends BaseEntity {

    @Column(nullable = false)
    private long userId;

    @Column(nullable = false)
    private long categoryId;

    @Column(nullable = false)
    private String description;

    @Column(length = 300)
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecurrenceType type;

    private String frequencyValues;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EndType endType;

    private Integer endCount;

    private LocalTime notificationTime;

    @Column(nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean isDeleted;

    @Column(nullable = false)
    private LocalDate lastGeneratedDate;

    @Builder
    public Recurrence(long userId, long categoryId, String description, String memo, RecurrenceType type,
                      String frequencyValues, LocalDate startDate, LocalDate endDate, EndType endType,
                      Integer endCount, LocalTime notificationTime, LocalDate lastGeneratedDate) {
        this.userId = userId;
        this.categoryId = categoryId;
        this.description = description;
        this.memo = memo;
        this.type = type;
        this.frequencyValues = frequencyValues;
        this.startDate = startDate;
        this.endDate = endDate;
        this.endType = endType != null ? endType : (endDate != null ? EndType.UNTIL : EndType.NONE);
        this.endCount = endCount;
        this.notificationTime = notificationTime;
        this.isDeleted = false;
        this.lastGeneratedDate = lastGeneratedDate;
    }

    public boolean isAlarmEnabled() {
        return notificationTime != null;
    }

    public void updateLastGeneratedDate(LocalDate date) {
        this.lastGeneratedDate = date;
    }

    public void update(RecurrenceType type, String frequencyValues, LocalDate startDate,
                      LocalDate endDate, LocalTime notificationTime) {
        this.type = type;
        this.frequencyValues = frequencyValues;
        this.startDate = startDate;
        this.endDate = endDate;
        this.endType = (endDate != null) ? EndType.UNTIL : EndType.NONE;
        this.notificationTime = notificationTime;
    }

    /** 반복 규칙 자체를 변경 (type, frequencyValues, startDate, endDate) */
    public void updateRule(RecurrenceType type, String frequencyValues, LocalDate startDate, LocalDate endDate) {
        this.type = type;
        this.frequencyValues = frequencyValues;
        this.startDate = startDate;
        this.endDate = endDate;
        this.endType = (endDate != null) ? EndType.UNTIL : EndType.NONE;
    }

    /** this_and_future 수정/삭제 시 기존 Master를 exclusiveDate 하루 전에 종료 */
    public void terminateAt(LocalDate exclusiveDate) {
        this.endType = EndType.UNTIL;
        this.endDate = exclusiveDate.minusDays(1);
    }

    /** 전체 삭제 시 Master soft delete */
    public void markDeleted() {
        this.isDeleted = true;
    }

    public void updateContent(String description, String memo, LocalTime notificationTime) {
        if (description != null) this.description = description;
        if (memo != null) this.memo = memo;
        this.notificationTime = notificationTime;
    }
}
