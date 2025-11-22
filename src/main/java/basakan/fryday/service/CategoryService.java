package basakan.fryday.service;

import basakan.fryday.controller.dto.CategoryCreateRequest;
import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.domain.Category;
import basakan.fryday.domain.CategoryColor;
import basakan.fryday.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private static final int MAX_CATEGORIES_COUNT = 6;

    @Transactional
    public Long createCategory(CategoryCreateRequest request) {
        long currentCount = categoryRepository.countByUserIdAndDeletedAtIsNull(request.getUserId());

        if (currentCount >= MAX_CATEGORIES_COUNT) {
            throw new BusinessException(ErrorCode.CATEGORY_LIMIT_EXCEEDED);
        }

        Category category = Category.builder()
                .name(request.getName())
                .color(request.getColor())
                .userId(request.getUserId())
                .build();

        return categoryRepository.save(category).getId();
    }

    @Transactional
    public void deleteCategory(Long categoryId, Long userId) {
        Category category = categoryRepository.findByIdAndUserIdAndDeletedAtIsNull(categoryId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        category.delete();
    }

    @Transactional
    public void initDefaultCategories(Long userId) {
        List<Category> defaultCategories = List.of(
                Category.builder()
                        .name("카테고리1")
                        .color(CategoryColor.RED)
                        .userId(userId)
                        .build(),
                Category.builder()
                        .name("카테고리2")
                        .color(CategoryColor.BLUE)
                        .userId(userId)
                        .build(),
                Category.builder()
                        .name("카테고리3")
                        .color(CategoryColor.GREEN)
                        .userId(userId)
                        .build()
        );
        categoryRepository.saveAll(defaultCategories);
    }
}
