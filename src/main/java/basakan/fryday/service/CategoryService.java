package basakan.fryday.service;

import basakan.fryday.controller.dto.CategoryCreateRequest;
import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.controller.dto.CategoryResponse;
import basakan.fryday.controller.dto.CategoryUpdateRequest;
import basakan.fryday.controller.dto.OrderUpdateRequest;
import basakan.fryday.domain.BaseEntity;
import basakan.fryday.domain.Category;
import basakan.fryday.domain.CategoryColor;
import basakan.fryday.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private static final int MAX_CATEGORIES_COUNT = 6;

    @Transactional
    public CategoryResponse createCategory(CategoryCreateRequest request) {
        long currentCount = categoryRepository.countByUserIdAndDeletedAtIsNull(request.getUserId());

        if (currentCount >= MAX_CATEGORIES_COUNT) {
            throw new BusinessException(ErrorCode.CATEGORY_LIMIT_EXCEEDED);
        }

        Long maxOrder = categoryRepository.findMaxDisplayOrder(request.getUserId());
        long nextOrder = (maxOrder == null) ? 1 : maxOrder + 1;

        Category category = Category.builder()
                .name(request.getName())
                .color(request.getColor())
                .userId(request.getUserId())
                .displayOrder(nextOrder)
                .build();

        Category savedCategory = categoryRepository.save(category);

        return CategoryResponse.from(savedCategory);
    }

    @Transactional
    public CategoryResponse updateCategory(Long categoryId, Long userId, CategoryUpdateRequest request) {
        Category category = categoryRepository.findByIdAndUserIdAndDeletedAtIsNull(categoryId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        category.update(request.getName(), request.getColor());

        return CategoryResponse.from(category);
    }

    @Transactional
    public void deleteCategory(Long categoryId, Long userId) {
        Category category = categoryRepository.findByIdAndUserIdAndDeletedAtIsNull(categoryId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        category.delete();
    }

    @Transactional
    public void reorderCategories(Long userId, OrderUpdateRequest request) {
        List<Long> idList = request.getIds();

        List<Category> categories = categoryRepository.findAllByUserIdAndDeletedAtIsNullOrderByDisplayOrderAsc(userId);

        Map<Long, Category> categoryMap = categories.stream()
                .collect(Collectors.toMap(BaseEntity::getId, c -> c));

        for (int i = 0; i < idList.size(); i++) {
            Category category = categoryMap.get(idList.get(i));
            if (category != null) {
                category.updateDisplayOrder((long) (i + 1));
            }
        }
    }

    @Transactional
    public void initDefaultCategories(Long userId) {
        List<Category> defaultCategories = List.of(
                Category.builder()
                        .name("카테고리1")
                        .color(CategoryColor.BR)
                        .userId(userId)
                        .build(),
                Category.builder()
                        .name("카테고리2")
                        .color(CategoryColor.CB)
                        .userId(userId)
                        .build(),
                Category.builder()
                        .name("카테고리3")
                        .color(CategoryColor.YL)
                        .userId(userId)
                        .build()
        );
        categoryRepository.saveAll(defaultCategories);
    }
}
