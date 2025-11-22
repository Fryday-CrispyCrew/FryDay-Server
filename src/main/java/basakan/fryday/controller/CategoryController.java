package basakan.fryday.controller;

import basakan.fryday.controller.dto.CategoryCreateRequest;
import basakan.fryday.common.response.ApiResponse;
import basakan.fryday.controller.dto.CategoryResponse;
import basakan.fryday.controller.dto.CategoryUpdateRequest;
import basakan.fryday.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

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
}

