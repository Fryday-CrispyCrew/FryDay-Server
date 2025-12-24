package basakan.fryday.controller.user.request;

import jakarta.validation.constraints.NotBlank;

public record SetNicknameRequest(
        @NotBlank(message = "닉네임은 필수 입력 항목입니다.")
        String nickname
) {
}
