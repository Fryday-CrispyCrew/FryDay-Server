package basakan.fryday.controller.group.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GroupCreateRequest {

    @NotBlank(message = "그룹 이름은 필수입니다.")
    @Size(max = 10, message = "그룹 이름은 최대 10자까지 가능합니다.")
    private String name;

    public GroupCreateRequest(String name) {
        this.name = name;
    }
}
