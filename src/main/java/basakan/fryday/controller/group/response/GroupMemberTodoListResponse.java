package basakan.fryday.controller.group.response;

import basakan.fryday.controller.todo.response.CharacterStatusResponse;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
public class GroupMemberTodoListResponse {

    private final Long userId;
    private final LocalDate date;
    private final CharacterStatusResponse characterStatus;
    private final List<GroupMemberCategoryTodosResponse> categories;

    private GroupMemberTodoListResponse(Long userId, LocalDate date, CharacterStatusResponse characterStatus,
                                        List<GroupMemberCategoryTodosResponse> categories) {
        this.userId = userId;
        this.date = date;
        this.characterStatus = characterStatus;
        this.categories = categories;
    }

    public static GroupMemberTodoListResponse of(Long userId, LocalDate date, CharacterStatusResponse characterStatus,
                                                 List<GroupMemberCategoryTodosResponse> categories) {
        return new GroupMemberTodoListResponse(userId, date, characterStatus, categories);
    }
}
