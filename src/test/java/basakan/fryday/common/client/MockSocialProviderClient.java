package basakan.fryday.common.client;

import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.repository.auth.client.SocialProviderClient;
import basakan.fryday.repository.auth.client.SocialUserInfo;

public class MockSocialProviderClient implements SocialProviderClient {

    private final AuthProvider provider;

    public MockSocialProviderClient(AuthProvider provider) {
        this.provider = provider;
    }

    @Override
    public AuthProvider getProvider() {
        return provider;
    }

    @Override
    public SocialUserInfo getUserInfo(String accessToken, String idToken) {
        return new SocialUserInfo(
                provider,
                "mock-provider-user-id-" + accessToken
        );
    }
}
