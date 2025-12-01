package basakan.fryday.repository.auth.client.apple;

import basakan.fryday.common.exception.auth.InvalidProviderTokenException;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
class ApplePublicKeys {

    @JsonProperty("keys")
    private List<Key> keys;

    @Getter
    @NoArgsConstructor
    static class Key {
        @JsonProperty("kty")
        private String kty;

        @JsonProperty("kid")
        private String kid;

        @JsonProperty("use")
        private String use;

        @JsonProperty("alg")
        private String alg;

        @JsonProperty("n")
        private String n;  // RSA modulus

        @JsonProperty("e")
        private String e;  // RSA exponent
    }

    public Key getKeyById(String kid) {
        return keys.stream()
                .filter(key -> key.getKid().equals(kid))
                .findFirst()
                .orElseThrow(InvalidProviderTokenException::new);
    }
}
