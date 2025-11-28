package basakan.fryday.repository.auth.client.naver;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
class NaverUserInfoResponse {

    @JsonProperty("resultcode")
    private String resultCode;

    @JsonProperty("message")
    private String message;

    @JsonProperty("response")
    private ResponseData response;

    @Getter
    @NoArgsConstructor
    static class ResponseData {
        @JsonProperty("id")
        private String id;
    }

    public String getSocialId() {
        return response != null ? response.getId() : null;
    }
}
