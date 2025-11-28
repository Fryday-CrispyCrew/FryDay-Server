package basakan.fryday.repository.auth.client;

import basakan.fryday.domain.auth.AuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SocialProviderClientFactory {

    private final List<SocialProviderClient> clients;

    private Map<AuthProvider, SocialProviderClient> clientMap;

    private Map<AuthProvider, SocialProviderClient> getClientMap() {
        if (clientMap == null) {
            clientMap = clients.stream()
                    .collect(Collectors.toMap(
                            SocialProviderClient::getProvider,
                            Function.identity()
                    ));
        }
        return clientMap;
    }

    public SocialProviderClient getClient(AuthProvider provider) {
        SocialProviderClient client = getClientMap().get(provider);
        if (client == null) {
            throw new IllegalArgumentException("Unsupported provider: " + provider);
        }
        return client;
    }
}
