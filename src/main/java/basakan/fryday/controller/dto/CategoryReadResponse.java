package basakan.fryday.controller.dto;

import basakan.fryday.domain.Category;
import lombok.Getter;

@Getter
public class CategoryReadResponse {

    private final Long id;
    private final String name;
    private final String colorCode;
    private final String colorHex;
    private final Long displayOrder;

    public CategoryReadResponse(Category category) {
        this.id = category.getId();
        this.name = category.getName();
        this.colorCode = category.getColor().getCode();
        this.colorHex = category.getColor().getHex();
        this.displayOrder = category.getDisplayOrder();
    }

    public static CategoryReadResponse from(Category category) {
        return new CategoryReadResponse(category);
    }
}
