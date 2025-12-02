package basakan.fryday.repository;

import basakan.fryday.domain.category.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    long countByUserIdAndDeletedAtIsNull(Long userId);

    Optional<Category> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    @Query("SELECT MAX(c.displayOrder) FROM Category c WHERE c.userId = :userId AND c.deletedAt IS NULL")
    Long findMaxDisplayOrder(@Param("userId") Long userId);

    List<Category> findAllByUserIdAndDeletedAtIsNullOrderByDisplayOrderAsc(Long userId);
}

