package com.amateur.howmuchfinanceconnector.infra.codef;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.codef")
public record CodefProperties(
        String baseUrl,
        String oauthUrl,
        String clientId,
        String clientSecret,
        Boolean encodeBody,
        Duration tokenRefreshSkew
) {
    public CodefProperties {
        baseUrl = defaultValue(baseUrl, "https://development.codef.io");
        oauthUrl = defaultValue(oauthUrl, "https://oauth.codef.io/oauth/token");
        clientId = defaultValue(clientId, "");
        clientSecret = defaultValue(clientSecret, "");
        encodeBody = encodeBody == null || encodeBody;
        tokenRefreshSkew = tokenRefreshSkew == null ? Duration.ofMinutes(1) : tokenRefreshSkew;
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
