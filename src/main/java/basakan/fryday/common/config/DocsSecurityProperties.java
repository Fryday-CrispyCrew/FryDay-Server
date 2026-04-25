package basakan.fryday.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "docs.security")
public record DocsSecurityProperties(
        String username,
        String password
) {
}
