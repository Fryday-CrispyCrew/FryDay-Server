package basakan.fryday.repository.auth.client.kakao;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
class KakaoUserInfoResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("kakao_account")
    private KakaoAccount kakaoAccount;

    public String getSocialId() {
        return id != null ? String.valueOf(id) : null;
    }

    public String getEmail() {
        return kakaoAccount != null ? kakaoAccount.getEmail() : null;
    }

    @Getter
    @NoArgsConstructor
    static class KakaoAccount {
        @JsonProperty("email")
        private String email;
    }
}
