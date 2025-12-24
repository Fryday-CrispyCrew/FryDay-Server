package basakan.fryday.controller.user.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshTokenResponse {

    private String accessToken;
    private String refreshToken;

    public static RefreshTokenResponse of(String accessToken, String refreshToken) {
        return RefreshTokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
}
