package basakan.fryday.repository.auth.client;

import basakan.fryday.domain.user.AuthProvider;

public record SocialUserInfo(
        AuthProvider provider,
        String providerUserId,
        String email
) {
}
