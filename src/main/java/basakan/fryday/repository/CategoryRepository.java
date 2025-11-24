package basakan.fryday.repository;

import basakan.fryday.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    long countByUserIdAndDeletedAtIsNull(Long userId);

    Optional<Category> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
}

