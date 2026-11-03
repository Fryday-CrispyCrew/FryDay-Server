package basakan.fryday.controller.group.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class InviteShareRequest {

    @NotNull(message = "공유 위치는 필수입니다.")
    @Pattern(regexp = "create_complete|group_setting", message = "지원하지 않는 공유 위치입니다.")
    private String source;

    @NotNull(message = "공유 결과는 필수입니다.")
    @Pattern(regexp = "shared|dismissed|unknown", message = "지원하지 않는 공유 결과입니다.")
    private String result;

    public InviteShareRequest(String source, String result) {
        this.source = source;
        this.result = result;
    }
}
