package basakan.fryday.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoryColor color;

    @Column(nullable = false)
    private Long userId;

    @Builder
    public Category(String name, CategoryColor color, Long userId) {
        this.name = name;
        this.color = color;
        this.userId = userId;
    }

    public void update(String name, CategoryColor color) {
        this.name = name;
        this.color = color;
    }
}