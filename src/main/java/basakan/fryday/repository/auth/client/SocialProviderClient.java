package basakan.fryday.repository.auth.client;

import basakan.fryday.domain.auth.AuthProvider;

public interface SocialProviderClient {

    AuthProvider getProvider();

    SocialUserInfo verifyToken(String accessToken, String idToken);
}
