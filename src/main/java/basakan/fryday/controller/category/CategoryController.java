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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ApiResponse<List<CategoryReadResponse>> getCategories() {
        Long currentUserId = 1L;

        List<CategoryReadResponse> responses = categoryService.getCategoriesByUserId(currentUserId);
        return ApiResponse.success(responses);
    }

    @PostMapping
    public ApiResponse<CategoryResponse> createCategory(@Valid @RequestBody CategoryCreateRequest request) {
        CategoryResponse response = categoryService.createCategory(request);

        return ApiResponse.success(response);
    }

    @PatchMapping("/{categoryId}")
    public ApiResponse<CategoryResponse> updateCategory(
            @PathVariable Long categoryId,
            @Valid @RequestBody CategoryUpdateRequest request
    ) {
        Long currentUserId = 1L;

        CategoryResponse response = categoryService.updateCategory(categoryId, currentUserId, request);

        return ApiResponse.success(response, "카테고리가 수정되었습니다.");
    }

    @DeleteMapping("/{categoryId}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long categoryId) {
        // TODO: 인증 정보에서 userId를 가져와서 전달해야 함
        Long currentUserId = 1L;

        categoryService.deleteCategory(categoryId, currentUserId);
        return ApiResponse.success(null, "카테고리가 삭제되었습니다.");
    }

    @PatchMapping("/reorder")
    public ApiResponse<Void> updateCategoryOrder(@Valid @RequestBody OrderUpdateRequest request) {
        Long currentUserId = 1L;
        categoryService.reorderCategories(currentUserId, request);
        return ApiResponse.success(null, "카테고리 순서가 변경되었습니다.");
    }
}

