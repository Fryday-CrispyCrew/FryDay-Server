package basakan.fryday.service.auth.dto;

import basakan.fryday.domain.auth.AuthProvider;

public record SocialLoginServiceDto(
        AuthProvider provider,
        String accessToken,
        String idToken
) {
}
