package com.amateur.howmuchfinanceconnector.adapter.out.codef;

import com.amateur.howmuchfinanceconnector.infra.codef.CodefProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

@Component
public class CodefBankClient {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String TRANSACTION_PATH = "/v1/kr/bank/p/account/transaction-list";

    private final RestClient api;
    private final RestClient oauth;
    private final CodefProperties properties;
    private final ObjectMapper objectMapper;
    private volatile Token token;

    public CodefBankClient(
            @Qualifier("codefApiRestClient") RestClient api,
            @Qualifier("codefOAuthRestClient") RestClient oauth,
            CodefProperties properties,
            ObjectMapper objectMapper
    ) {
        this.api = api;
        this.oauth = oauth;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public List<JsonNode> fetch(String connectedId, String organization, String account, LocalDate from, LocalDate to) {
        return fetch(connectedId, organization, account, from, to, false);
    }

    private List<JsonNode> fetch(
            String connectedId,
            String organization,
            String account,
            LocalDate from,
            LocalDate to,
            boolean retried
    ) {
        try {
            JsonNode response = response(api.post()
                    .uri(TRANSACTION_PATH)
                    .headers(headers -> headers.setBearerAuth(accessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body(Map.of(
                            "connectedId", connectedId,
                            "organization", organization,
                            "account", account,
                            "orderBy", "1",
                            "startDate", DATE.format(from),
                            "endDate", DATE.format(to)
                    )))
                    .retrieve()
                    .body(String.class));
            String code = response.at("/result/code").asText();
            if (!"CF-00000".equals(code)) {
                throw new CodefCollectionException(code.isBlank() ? "CODEF_REQUEST_FAILED" : code);
            }
            JsonNode data = response.path("data");
            for (String name : List.of("resTrHistoryList", "resTransactionList", "resAccountTransactionList", "resAccountTrList")) {
                if (data.path(name).isArray()) return data.path(name).valueStream().toList();
            }
            if (data.isArray()) return data.valueStream().toList();
            return List.of();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 401 && !retried) {
                token = null;
                return fetch(connectedId, organization, account, from, to, true);
            }
            throw new CodefCollectionException(errorCode(exception));
        } catch (RestClientException exception) {
            throw new CodefCollectionException("CODEF_NETWORK_ERROR");
        }
    }

    private synchronized String accessToken() {
        Instant now = Instant.now();
        if (token != null && token.expiresAt().minus(properties.tokenRefreshSkew()).isAfter(now)) {
            return token.value();
        }
        if (properties.clientId().isBlank() || properties.clientSecret().isBlank()) {
            throw new CodefCollectionException("CODEF_CONFIGURATION_MISSING");
        }
        String basic = Base64.getEncoder().encodeToString(
                (properties.clientId() + ":" + properties.clientSecret()).getBytes(StandardCharsets.UTF_8)
        );
        TokenResponse response = oauth.post()
                .uri(properties.oauthUrl())
                .header("Authorization", "Basic " + basic)
                .body("grant_type=client_credentials&scope=read")
                .retrieve()
                .body(TokenResponse.class);
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new CodefCollectionException("CODEF_TOKEN_FAILED");
        }
        token = new Token(response.accessToken(), now.plusSeconds(Math.max(0, response.expiresIn())));
        return token.value();
    }

    private String body(Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            return properties.encodeBody() ? UriUtils.encode(json, StandardCharsets.UTF_8) : json;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("CODEF request cannot be serialized", exception);
        }
    }

    private JsonNode response(String raw) {
        try {
            return objectMapper.readTree(raw == null ? "{}" : raw);
        } catch (JsonProcessingException first) {
            try {
                return objectMapper.readTree(URLDecoder.decode(raw, StandardCharsets.UTF_8));
            } catch (JsonProcessingException | IllegalArgumentException second) {
                throw new CodefCollectionException("CODEF_RESPONSE_INVALID");
            }
        }
    }

    private String errorCode(RestClientResponseException exception) {
        try {
            String code = response(exception.getResponseBodyAsString()).at("/result/code").asText();
            return code.isBlank() ? "CODEF_HTTP_" + exception.getStatusCode().value() : code;
        } catch (CodefCollectionException ignored) {
            return "CODEF_HTTP_" + exception.getStatusCode().value();
        }
    }

    private record Token(String value, Instant expiresAt) {}
    private record TokenResponse(@JsonProperty("access_token") String accessToken,
                                 @JsonProperty("expires_in") long expiresIn) {}

    public static class CodefCollectionException extends RuntimeException {
        public CodefCollectionException(String code) { super(code); }
    }
}
