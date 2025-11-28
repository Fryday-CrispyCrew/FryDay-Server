package basakan.fryday.repository.auth.client.naver;

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
public class NaverOAuthClient implements SocialProviderClient {

    private static final String NAVER_USER_INFO_URL = "https://openapi.naver.com/v1/nid/me";

    private final RestTemplate restTemplate;

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.NAVER;
    }

    @Override
    public SocialUserInfo verifyToken(String accessToken, String idToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<NaverUserInfoResponse> response = restTemplate.exchange(
                    NAVER_USER_INFO_URL,
                    HttpMethod.GET,
                    request,
                    NaverUserInfoResponse.class
            );

            NaverUserInfoResponse userResponse = response.getBody();
            String socialId = userResponse != null ? userResponse.getSocialId() : null;

            if (socialId == null) {
                throw new InvalidProviderTokenException();
            }

            return new SocialUserInfo(AuthProvider.NAVER, socialId);
        } catch (Exception e) {
            throw new InvalidProviderTokenException();
        }
    }
}
