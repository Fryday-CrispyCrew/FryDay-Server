package basakan.fryday.controller.group.request;

import basakan.fryday.domain.category.Category;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class GroupPublicCategoryUpdateRequest {

    @NotEmpty(message = "공개 카테고리는 최소 1개 이상 선택해야 합니다.")
    @Size(max = Category.MAX_COUNT_PER_USER, message = "공개 카테고리는 최대 6개까지 선택할 수 있습니다.")
    private List<Long> categoryIds;

    public GroupPublicCategoryUpdateRequest(List<Long> categoryIds) {
        this.categoryIds = categoryIds;
    }
}
