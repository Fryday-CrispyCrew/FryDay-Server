package basakan.fryday.domain.report;

import basakan.fryday.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "monthly_reports",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_monthly_report_user_year_month",
            columnNames = {"user_id", "year", "month"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MonthlyReport extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int year;

    @Column(nullable = false)
    private int month;

    @Column(nullable = false)
    private int totalTodos;

    @Column(nullable = false)
    private int completedTodos;

    @Column(nullable = false)
    private int incompleteTodos;

    @Column(nullable = false)
    private int attendanceDays;

    @Column(nullable = false)
    private double achievementRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceIcon attendanceIcon;

    @Column(nullable = false, length = 100)
    private String attendanceMessage;

    @OneToMany(mappedBy = "monthlyReport", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MonthlyReportCategory> categories = new ArrayList<>();

    @Builder
    public MonthlyReport(Long userId, int year, int month,
                         int totalTodos, int completedTodos, int incompleteTodos,
                         int attendanceDays, double achievementRate,
                         AttendanceIcon attendanceIcon, String attendanceMessage) {
        this.userId = userId;
        this.year = year;
        this.month = month;
        this.totalTodos = totalTodos;
        this.completedTodos = completedTodos;
        this.incompleteTodos = incompleteTodos;
        this.attendanceDays = attendanceDays;
        this.achievementRate = achievementRate;
        this.attendanceIcon = attendanceIcon;
        this.attendanceMessage = attendanceMessage;
    }

    public void addCategory(MonthlyReportCategory category) {
        this.categories.add(category);
        category.assignReport(this);
    }
}
