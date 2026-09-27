package basakan.fryday.controller.group.response;

import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
public class GroupMemberTodoListResponse {

    private final Long userId;
    private final LocalDate date;
    private final List<GroupMemberCategoryTodosResponse> categories;

    private GroupMemberTodoListResponse(Long userId, LocalDate date, List<GroupMemberCategoryTodosResponse> categories) {
        this.userId = userId;
        this.date = date;
        this.categories = categories;
    }

    public static GroupMemberTodoListResponse of(Long userId, LocalDate date,
                                                 List<GroupMemberCategoryTodosResponse> categories) {
        return new GroupMemberTodoListResponse(userId, date, categories);
    }
}
