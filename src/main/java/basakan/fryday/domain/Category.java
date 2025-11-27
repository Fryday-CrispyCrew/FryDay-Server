package basakan.fryday.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseEntity{

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoryColor color;

    @Column(nullable = false)
    private Long userId;

    private LocalDateTime deletedAt;

    @Column(nullable = false)
    private Long displayOrder;

    @Builder
    public Category(String name, CategoryColor color, Long userId, Long displayOrder) {
        this.name = name;
        this.color = color;
        this.userId = userId;
        this.displayOrder = displayOrder != null ? displayOrder : 0L;
    }

    public void update(String name, CategoryColor color) {
        this.name = name;
        this.color = color;
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public void updateDisplayOrder(Long displayOrder) {
        this.displayOrder = displayOrder;
    }
}