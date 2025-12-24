package basakan.fryday.controller.user.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NicknameCheckResponse {

    private boolean available;
    private String message;

    public static NicknameCheckResponse available() {
        return new NicknameCheckResponse(true, "사용 가능한 닉네임입니다.");
    }

    public static NicknameCheckResponse unavailable() {
        return new NicknameCheckResponse(false, "이미 사용 중인 닉네임입니다.");
    }
}
