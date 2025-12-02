package basakan.fryday.repository.auth.client.apple;

import basakan.fryday.common.exception.auth.InvalidProviderTokenException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
public class AppleJwksClient {

    private final WebClient webClient;

    @Value("${oauth.apple.jwks-url:https://appleid.apple.com/auth/keys}")
    private String appleJwksUrl;

    public ApplePublicKeys fetchPublicKeys() {
        try {
            ApplePublicKeys keys = webClient.get()
                    .uri(appleJwksUrl)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.createException()
                                    .map(error -> new InvalidProviderTokenException())
                    )
                    .bodyToMono(ApplePublicKeys.class)
                    .block();

            if (keys == null || keys.getKeys() == null || keys.getKeys().isEmpty()) {
                throw new InvalidProviderTokenException();
            }

            return keys;
        } catch (InvalidProviderTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidProviderTokenException();
        }
    }
}
