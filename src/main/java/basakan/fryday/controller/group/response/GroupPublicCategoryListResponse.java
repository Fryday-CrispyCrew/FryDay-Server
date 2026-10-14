package basakan.fryday.controller.group.response;

import lombok.Getter;

import java.util.List;

@Getter
public class GroupPublicCategoryListResponse {

    private final List<GroupPublicCategoryResponse> categories;

    private GroupPublicCategoryListResponse(List<GroupPublicCategoryResponse> categories) {
        this.categories = categories;
    }

    public static GroupPublicCategoryListResponse from(List<GroupPublicCategoryResponse> categories) {
        return new GroupPublicCategoryListResponse(categories);
    }
}
