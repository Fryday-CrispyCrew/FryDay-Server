package basakan.fryday.repository.auth.client.kakao;

import basakan.fryday.common.exception.auth.InvalidProviderTokenException;
import basakan.fryday.domain.auth.AuthProvider;
import basakan.fryday.repository.auth.client.SocialProviderClient;
import basakan.fryday.repository.auth.client.SocialUserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class KakaoOAuthClient implements SocialProviderClient {

    private static final String KAKAO_USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    private final RestTemplate restTemplate;

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.KAKAO;
    }

    @Override
    public SocialUserInfo verifyToken(String accessToken, String idToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<KakaoUserInfoResponse> response = restTemplate.exchange(
                    KAKAO_USER_INFO_URL,
                    HttpMethod.GET,
                    request,
                    KakaoUserInfoResponse.class
            );

            KakaoUserInfoResponse userResponse = response.getBody();
            String socialId = userResponse != null ? userResponse.getSocialId() : null;

            if (socialId == null) {
                throw new InvalidProviderTokenException();
            }

            return new SocialUserInfo(AuthProvider.KAKAO, socialId);
        } catch (Exception e) {
            throw new InvalidProviderTokenException();
        }
    }
}
