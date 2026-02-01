package basakan.fryday.repository.auth.client.naver;

import basakan.fryday.common.exception.auth.InvalidProviderTokenException;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.repository.auth.client.SocialProviderClient;
import basakan.fryday.repository.auth.client.SocialUserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
public class NaverOAuthClient implements SocialProviderClient {

    private static final String NAVER_USER_INFO_URL = "https://openapi.naver.com/v1/nid/me";

    private final WebClient webClient;

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.NAVER;
    }

    @Override
    public SocialUserInfo getUserInfo(String accessToken, String idToken) {
        try {
            NaverUserInfoResponse userResponse = webClient.get()
                    .uri(NAVER_USER_INFO_URL)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.createException()
                                    .map(error -> new InvalidProviderTokenException())
                    )
                    .bodyToMono(NaverUserInfoResponse.class)
                    .block();

            String socialId = userResponse != null ? userResponse.getSocialId() : null;

            if (socialId == null) {
                throw new InvalidProviderTokenException();
            }

            String email = userResponse.getEmail();
            return new SocialUserInfo(AuthProvider.NAVER, socialId, email);
        } catch (InvalidProviderTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidProviderTokenException();
        }
    }
}
