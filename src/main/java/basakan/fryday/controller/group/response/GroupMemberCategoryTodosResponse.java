package basakan.fryday.controller.group.response;

import basakan.fryday.domain.category.Category;
import lombok.Getter;

import java.util.List;

@Getter
public class GroupMemberCategoryTodosResponse {

    private final Long categoryId;
    private final String name;
    private final String colorCode;
    private final String colorHex;
    private final List<GroupMemberTodoResponse> todos;

    private GroupMemberCategoryTodosResponse(Category category, List<GroupMemberTodoResponse> todos) {
        this.categoryId = category.getId();
        this.name = category.getName();
        this.colorCode = category.getColor().getCode();
        this.colorHex = category.getColor().getHex();
        this.todos = todos;
    }

    public static GroupMemberCategoryTodosResponse of(Category category, List<GroupMemberTodoResponse> todos) {
        return new GroupMemberCategoryTodosResponse(category, todos);
    }
}
