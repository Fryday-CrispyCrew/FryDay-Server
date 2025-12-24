package basakan.fryday.repository.auth.client;

import basakan.fryday.domain.user.AuthProvider;

public interface SocialProviderClient {

    AuthProvider getProvider();

    SocialUserInfo getUserInfo(String accessToken, String idToken);
}
