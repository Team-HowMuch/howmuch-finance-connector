package com.amateur.howmuchfinanceconnector.adapter.out.codef;

import com.amateur.howmuchfinanceconnector.application.sync.FinanceCollectionException;
import com.amateur.howmuchfinanceconnector.domain.sync.FinanceCollectionFailureCode;
import com.amateur.howmuchfinanceconnector.infra.codef.config.CodefProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Component
public class CodefTokenClient {

    private static final String REQUEST_BODY = "grant_type=client_credentials&scope=read";

    private final RestClient restClient;
    private final CodefProperties properties;
    private volatile CachedToken cachedToken;

    public CodefTokenClient(
            @Qualifier("codefOAuthRestClient") RestClient restClient,
            CodefProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    String getAccessToken() {
        CachedToken current = cachedToken;
        if (current != null && current.usable()) {
            return current.value();
        }
        synchronized (this) {
            current = cachedToken;
            if (current == null || !current.usable()) {
                cachedToken = requestAccessToken();
            }
            return cachedToken.value();
        }
    }

    void clearCache() {
        cachedToken = null;
    }

    private CachedToken requestAccessToken() {
        if (!properties.hasClientCredentials()) {
            throw failure(FinanceCollectionFailureCode.INTERNAL_ERROR, false,
                    "CODEF 연동 설정을 확인할 수 없습니다.");
        }
        try {
            JsonNode response = restClient.post()
                    .uri(properties.oauthUrl())
                    .header(HttpHeaders.AUTHORIZATION, basicAuthorization())
                    .body(REQUEST_BODY)
                    .retrieve()
                    .body(JsonNode.class);
            String token = response == null ? null : response.path("access_token").asText(null);
            long expiresIn = response == null ? 0 : response.path("expires_in").asLong(0);
            if (token == null || token.isBlank() || expiresIn <= 0) {
                throw failure(FinanceCollectionFailureCode.UPSTREAM_UNAVAILABLE, true,
                        "CODEF 인증 서버를 사용할 수 없습니다.");
            }
            return new CachedToken(token, Instant.now().plusSeconds(expiresIn));
        } catch (FinanceCollectionException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw failure(FinanceCollectionFailureCode.UPSTREAM_UNAVAILABLE, true,
                    "CODEF 인증 서버를 사용할 수 없습니다.");
        }
    }

    private String basicAuthorization() {
        String credentials = properties.clientId() + ":" + properties.clientSecret();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private FinanceCollectionException failure(
            FinanceCollectionFailureCode code,
            boolean retryable,
            String message
    ) {
        return new FinanceCollectionException(code, retryable, message);
    }

    private record CachedToken(String value, Instant expiresAt) {
        boolean usable() {
            return expiresAt.minusSeconds(60).isAfter(Instant.now());
        }
    }
}
