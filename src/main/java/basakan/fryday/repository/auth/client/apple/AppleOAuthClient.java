package basakan.fryday.repository.auth.client.apple;

import basakan.fryday.common.exception.auth.InvalidProviderTokenException;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.repository.auth.client.SocialProviderClient;
import basakan.fryday.repository.auth.client.SocialUserInfo;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.PublicKey;

@Component
@RequiredArgsConstructor
public class AppleOAuthClient implements SocialProviderClient {

    private final AppleJwksClient appleJwksClient;
    private final AppleJwtParser appleJwtParser;

    @Value("${oauth.apple.client-id}")
    private String appleClientId;

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.APPLE;
    }

    @Override
    public SocialUserInfo getUserInfo(String accessToken, String idToken) {
        if (idToken == null || idToken.isEmpty()) {
            throw new InvalidProviderTokenException();
        }

        try {
            // 1. ID Token에서 kid 추출
            String kid = appleJwtParser.getKidFromToken(idToken);

            // 2. Apple JWKS 조회
            ApplePublicKeys publicKeys = appleJwksClient.fetchPublicKeys();

            // 3. kid로 매칭되는 공개키 찾기
            ApplePublicKeys.Key matchedKey = publicKeys.getKeyById(kid);

            // 4. JWK를 PublicKey로 변환
            PublicKey publicKey = appleJwtParser.generatePublicKey(matchedKey);

            // 5. JWT 서명 검증 + Claims 파싱
            Claims claims = appleJwtParser.parseAndVerify(
                    idToken,
                    publicKey,
                    appleClientId
            );

            // 6. subject 추출
            String subject = claims.getSubject();
            if (subject == null || subject.isEmpty()) {
                throw new InvalidProviderTokenException();
            }

            return new SocialUserInfo(AuthProvider.APPLE, subject);
        } catch (InvalidProviderTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidProviderTokenException();
        }
    }
}
