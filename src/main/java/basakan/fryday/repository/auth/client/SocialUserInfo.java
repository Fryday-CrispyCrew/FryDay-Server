package basakan.fryday.repository.auth.client;

import basakan.fryday.domain.auth.AuthProvider;

public record SocialUserInfo(
        AuthProvider provider,
        String providerUserId
) {
}
