package basakan.fryday.controller.category;

import basakan.fryday.common.response.ApiResponse;
import basakan.fryday.controller.category.request.CategoryCreateRequest;
import basakan.fryday.controller.category.request.CategoryUpdateRequest;
import basakan.fryday.controller.dto.OrderUpdateRequest;
import basakan.fryday.controller.category.response.CategoryReadResponse;
import basakan.fryday.controller.category.response.CategoryResponse;
import basakan.fryday.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ApiResponse<List<CategoryReadResponse>> getCategories(@AuthenticationPrincipal Long userId) {
        List<CategoryReadResponse> responses = categoryService.getCategoriesByUserId(userId);
        return ApiResponse.success(responses);
    }

    @PostMapping
    public ApiResponse<CategoryResponse> createCategory(@Valid @RequestBody CategoryCreateRequest request,
                                                        @AuthenticationPrincipal Long userId) {
        CategoryResponse response = categoryService.createCategory(request, userId);

        return ApiResponse.success(response);
    }

    @PatchMapping("/{categoryId}")
    public ApiResponse<CategoryResponse> updateCategory(
            @PathVariable Long categoryId,
            @Valid @RequestBody CategoryUpdateRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        CategoryResponse response = categoryService.updateCategory(categoryId, userId, request);

        return ApiResponse.success(response, "카테고리가 수정되었습니다.");
    }

    @DeleteMapping("/{categoryId}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long categoryId,
                                            @AuthenticationPrincipal Long userId) {
        categoryService.deleteCategory(categoryId, userId);
        return ApiResponse.success(null, "카테고리가 삭제되었습니다.");
    }

    @PatchMapping("/reorder")
    public ApiResponse<Void> updateCategoryOrder(@Valid @RequestBody OrderUpdateRequest request,
                                                 @AuthenticationPrincipal Long userId) {
        categoryService.reorderCategories(userId, request);
        return ApiResponse.success(null, "카테고리 순서가 변경되었습니다.");
    }
}

