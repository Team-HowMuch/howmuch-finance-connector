package com.amateur.howmuchfinanceconnector.infra.codef.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

@ConfigurationProperties(prefix = "finance.codef")
public record CodefProperties(
        String baseUrl,
        String oauthUrl,
        String clientId,
        String clientSecret,
        Duration connectTimeout,
        Duration readTimeout
) {
    public CodefProperties {
        baseUrl = defaultIfBlank(baseUrl, "https://development.codef.io");
        oauthUrl = defaultIfBlank(oauthUrl, "https://oauth.codef.io/oauth/token");
        clientId = defaultIfBlank(clientId, "");
        clientSecret = defaultIfBlank(clientSecret, "");
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(305) : readTimeout;
    }

    public boolean hasClientCredentials() {
        return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
    }

    private static String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
