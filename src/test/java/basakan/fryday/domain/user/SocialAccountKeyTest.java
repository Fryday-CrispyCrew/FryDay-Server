package basakan.fryday.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("소셜 계정 키")
class SocialAccountKeyTest {

    @Test
    @DisplayName("같은 소셜 계정은 같은 키이고, 로그인 방식이나 소셜 ID 가 다르면 다른 키다")
    void sameAccountSameKey() {
        String key = SocialAccountKey.of(AuthProvider.KAKAO, "12345");

        assertThat(SocialAccountKey.of(AuthProvider.KAKAO, "12345")).isEqualTo(key);
        assertThat(SocialAccountKey.of(AuthProvider.NAVER, "12345")).isNotEqualTo(key);
        assertThat(SocialAccountKey.of(AuthProvider.KAKAO, "54321")).isNotEqualTo(key);
    }

    @Test
    @DisplayName("소셜 ID 원문이 드러나지 않는 SHA-256 16진수 문자열이다")
    void hashesProviderUserId() {
        String key = SocialAccountKey.of(AuthProvider.APPLE, "001234.abcdef");

        assertThat(key).hasSize(64).matches("[0-9a-f]+").doesNotContain("abcdef");
    }
}
