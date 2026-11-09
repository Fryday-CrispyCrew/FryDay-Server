package basakan.fryday.domain.user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 탈퇴 후 계정이 지워져도 같은 소셜 계정의 재가입을 같은 사람으로 알아보기 위한 키.
 * 소셜 ID 원문을 이벤트 로그에 남기지 않으려고 해시한다.
 */
public final class SocialAccountKey {

    private SocialAccountKey() {
    }

    public static String of(AuthProvider provider, String providerUserId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((provider.name() + ":" + providerUserId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
