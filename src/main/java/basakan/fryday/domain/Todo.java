package basakan.fryday.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Todo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    public enum Status {
        IN_PROGRESS, // 튀기는 중(기본 상태)
        COMPLETED,   // 튀김(완료)
        FAILED       // 미완료(탐)
    }

    @Builder
    public Todo(String description) {
        this.description = description;
        this.status = Status.IN_PROGRESS;
    }

}
