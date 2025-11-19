package basakan.fryday.controller;

import basakan.fryday.api.dto.CategoryCreateRequest;
import basakan.fryday.common.response.ApiResponse;
import basakan.fryday.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    public ApiResponse<Long> createCategory(@Valid @RequestBody CategoryCreateRequest request) {
        Long saveId = categoryService.createCategory(request);
        return ApiResponse.success(saveId);
    }

}
