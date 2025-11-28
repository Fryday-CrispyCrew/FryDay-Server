package basakan.fryday.repository.auth.client.kakao;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
class KakaoUserInfoResponse {

    @JsonProperty("id")
    private Long id;

    public String getSocialId() {
        return id != null ? String.valueOf(id) : null;
    }
}
