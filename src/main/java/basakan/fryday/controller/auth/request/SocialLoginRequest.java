package basakan.fryday.controller.auth.request;

import basakan.fryday.domain.auth.AuthProvider;
import basakan.fryday.service.auth.dto.SocialLoginServiceDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SocialLoginRequest {

    @NotNull(message = "Provider는 필수입니다")
    private AuthProvider provider;

    @NotBlank(message = "AccessToken은 필수입니다")
    private String accessToken;

    private String idToken;

    public SocialLoginServiceDto toServiceDto() {
        return new SocialLoginServiceDto(provider, accessToken, idToken);
    }
}
