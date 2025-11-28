package basakan.fryday.repository.auth.client.apple;

import basakan.fryday.common.exception.auth.InvalidProviderTokenException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class AppleJwtParser {

    private final ObjectMapper objectMapper;

    public String getKidFromToken(String idToken) {
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length != 3) {
                throw new InvalidProviderTokenException();
            }

            String header = new String(Base64.getUrlDecoder().decode(parts[0]));
            JsonNode jsonNode = objectMapper.readTree(header);
            String kid = jsonNode.get("kid").asText();

            if (kid == null || kid.isEmpty()) {
                throw new InvalidProviderTokenException();
            }

            return kid;
        } catch (Exception e) {
            throw new InvalidProviderTokenException();
        }
    }

    public PublicKey generatePublicKey(ApplePublicKeys.Key key) {
        try {
            byte[] nBytes = Base64.getUrlDecoder().decode(key.getN());
            byte[] eBytes = Base64.getUrlDecoder().decode(key.getE());

            BigInteger n = new BigInteger(1, nBytes);
            BigInteger e = new BigInteger(1, eBytes);

            RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(n, e);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");

            return keyFactory.generatePublic(publicKeySpec);
        } catch (Exception e) {
            throw new InvalidProviderTokenException();
        }
    }

    public Claims parseAndVerify(String idToken, PublicKey publicKey, String clientId) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(idToken)
                    .getPayload();

            // iss 검증
            String issuer = claims.getIssuer();
            if (!"https://appleid.apple.com".equals(issuer)) {
                throw new InvalidProviderTokenException();
            }

            // aud 검증
            String audience = claims.getAudience().iterator().next();
            if (!clientId.equals(audience)) {
                throw new InvalidProviderTokenException();
            }

            // exp는 JJWT가 자동으로 검증함

            return claims;
        } catch (Exception e) {
            throw new InvalidProviderTokenException();
        }
    }
}
