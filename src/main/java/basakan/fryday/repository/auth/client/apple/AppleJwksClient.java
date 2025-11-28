package basakan.fryday.repository.auth.client.apple;

import basakan.fryday.common.exception.auth.InvalidProviderTokenException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class AppleJwksClient {

    private final RestTemplate restTemplate;

    @Value("${oauth.apple.jwks-url:https://appleid.apple.com/auth/keys}")
    private String appleJwksUrl;

    public ApplePublicKeys fetchPublicKeys() {
        try {
            ResponseEntity<ApplePublicKeys> response = restTemplate.exchange(
                    appleJwksUrl,
                    HttpMethod.GET,
                    null,
                    ApplePublicKeys.class
            );

            ApplePublicKeys keys = response.getBody();
            if (keys == null || keys.getKeys() == null || keys.getKeys().isEmpty()) {
                throw new InvalidProviderTokenException();
            }

            return keys;
        } catch (Exception e) {
            throw new InvalidProviderTokenException();
        }
    }
}
