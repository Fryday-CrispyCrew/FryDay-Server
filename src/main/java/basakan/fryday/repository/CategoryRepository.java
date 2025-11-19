package basakan.fryday.repository;

import basakan.fryday.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    long countByUserId(Long memberId);
}
