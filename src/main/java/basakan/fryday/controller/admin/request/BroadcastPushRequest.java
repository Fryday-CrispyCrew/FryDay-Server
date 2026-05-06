package basakan.fryday.controller.admin.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BroadcastPushRequest {

    @NotBlank(message = "알림 제목은 필수입니다.")
    @Size(max = 50, message = "알림 제목은 최대 50자까지 가능합니다.")
    private String title;

    @NotBlank(message = "알림 내용은 필수입니다.")
    @Size(max = 200, message = "알림 내용은 최대 200자까지 가능합니다.")
    private String body;

    public BroadcastPushRequest(String title, String body) {
        this.title = title;
        this.body = body;
    }
}
