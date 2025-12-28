package basakan.fryday.domain.report;

import basakan.fryday.domain.BaseEntity;
import basakan.fryday.domain.category.CategoryColor;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "monthly_report_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MonthlyReportCategory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monthly_report_id", nullable = false)
    private MonthlyReport monthlyReport;

    @Column(nullable = false, length = 50)
    private String categoryName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoryColor categoryColor;

    @Column(nullable = false)
    private int totalTodos;

    @Column(nullable = false)
    private int completedTodos;

    @Column(nullable = false)
    private int incompleteTodos;

    @Column(nullable = false)
    private double successRate;

    @Column(nullable = false)
    private double failureRate;

    @Builder
    public MonthlyReportCategory(String categoryName, CategoryColor categoryColor,
                                 int totalTodos, int completedTodos, int incompleteTodos,
                                 double successRate, double failureRate) {
        this.categoryName = categoryName;
        this.categoryColor = categoryColor;
        this.totalTodos = totalTodos;
        this.completedTodos = completedTodos;
        this.incompleteTodos = incompleteTodos;
        this.successRate = successRate;
        this.failureRate = failureRate;
    }

    void assignReport(MonthlyReport monthlyReport) {
        this.monthlyReport = monthlyReport;
    }
}
