package basakan.fryday.controller.group.request;

import basakan.fryday.domain.category.Category;
import basakan.fryday.domain.group.FryGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 참여와 공개 카테고리 설정을 한 번에 받는다.
 * 그룹마다 최소 1개는 공개해야 하므로, 카테고리 없이 그룹원이 되는 중간 상태를 만들지 않는다.
 */
@Getter
@NoArgsConstructor
public class GroupJoinRequest {

    @NotBlank(message = "초대 코드는 필수입니다.")
    @Size(min = FryGroup.INVITE_CODE_LENGTH, max = FryGroup.INVITE_CODE_LENGTH,
            message = "초대 코드는 6자리입니다.")
    private String inviteCode;

    @NotEmpty(message = "공개 카테고리는 최소 1개 이상 선택해야 합니다.")
    @Size(max = Category.MAX_COUNT_PER_USER, message = "공개 카테고리는 최대 6개까지 선택할 수 있습니다.")
    private List<Long> categoryIds;

    public GroupJoinRequest(String inviteCode, List<Long> categoryIds) {
        this.inviteCode = inviteCode;
        this.categoryIds = categoryIds;
    }
}
