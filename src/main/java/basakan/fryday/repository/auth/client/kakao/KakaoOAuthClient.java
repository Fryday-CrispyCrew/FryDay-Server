package basakan.fryday.repository.auth.client.kakao;

import basakan.fryday.common.exception.auth.InvalidProviderTokenException;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.repository.auth.client.SocialProviderClient;
import basakan.fryday.repository.auth.client.SocialUserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
public class KakaoOAuthClient implements SocialProviderClient {

    private static final String KAKAO_USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    private final WebClient webClient;

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.KAKAO;
    }

    @Override
    public SocialUserInfo getUserInfo(String accessToken, String idToken) {
        try {
            KakaoUserInfoResponse userResponse = webClient.get()
                    .uri(KAKAO_USER_INFO_URL)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.createException()
                                    .map(error -> new InvalidProviderTokenException())
                    )
                    .bodyToMono(KakaoUserInfoResponse.class)
                    .block();

            String socialId = userResponse != null ? userResponse.getSocialId() : null;

            if (socialId == null) {
                throw new InvalidProviderTokenException();
            }

            return new SocialUserInfo(AuthProvider.KAKAO, socialId);
        } catch (InvalidProviderTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidProviderTokenException();
        }
    }
}
