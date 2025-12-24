package basakan.fryday.repository.auth.client;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.domain.user.AuthProvider;
import jakarta.annotation.PostConstruct;
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

    @PostConstruct
    private void initializeClientMap() {
        this.clientMap = clients.stream()
                .collect(Collectors.toMap(
                        SocialProviderClient::getProvider,
                        Function.identity()
                ));
    }

    public SocialProviderClient getClient(AuthProvider provider) {
        SocialProviderClient client = clientMap.get(provider);
        if (client == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_PROVIDER);
        }
        return client;
    }
}
