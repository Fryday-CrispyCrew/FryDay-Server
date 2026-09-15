package basakan.fryday.controller.group.response;

import basakan.fryday.domain.category.Category;
import lombok.Getter;

@Getter
public class GroupPublicCategoryResponse {

    private final Long categoryId;
    private final String name;
    private final String colorCode;
    private final String colorHex;
    private final Long displayOrder;

    /** Boolean 래퍼여야 JSON 키가 isPublic 으로 나간다. primitive 면 public 으로 깎인다. */
    private final Boolean isPublic;

    private GroupPublicCategoryResponse(Category category, boolean isPublic) {
        this.categoryId = category.getId();
        this.name = category.getName();
        this.colorCode = category.getColor().getCode();
        this.colorHex = category.getColor().getHex();
        this.displayOrder = category.getDisplayOrder();
        this.isPublic = isPublic;
    }

    public static GroupPublicCategoryResponse of(Category category, boolean isPublic) {
        return new GroupPublicCategoryResponse(category, isPublic);
    }
}
