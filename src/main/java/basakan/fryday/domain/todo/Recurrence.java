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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecurrenceType type;

    private String frequencyValues;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    private LocalTime notificationTime;

    @Column(nullable = false)
    private LocalDate lastGeneratedDate;

    @Builder
    public Recurrence(long userId, long categoryId, String description, RecurrenceType type,
                      String frequencyValues, LocalDate startDate, LocalDate endDate,
                      LocalTime notificationTime, LocalDate lastGeneratedDate) {
        this.userId = userId;
        this.categoryId = categoryId;
        this.description = description;
        this.type = type;
        this.frequencyValues = frequencyValues;
        this.startDate = startDate;
        this.endDate = endDate;
        this.notificationTime = notificationTime;
        this.lastGeneratedDate = lastGeneratedDate;
    }

    public void updateLastGeneratedDate(LocalDate date) {
        this.lastGeneratedDate = date;
    }
}
