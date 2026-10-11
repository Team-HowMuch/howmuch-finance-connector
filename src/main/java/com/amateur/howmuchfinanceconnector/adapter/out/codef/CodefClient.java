package com.amateur.howmuchfinanceconnector.adapter.out.codef;

import com.amateur.howmuchfinanceconnector.application.sync.FinanceCollectionException;
import com.amateur.howmuchfinanceconnector.domain.sync.FinanceCollectionFailureCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Component
public class CodefClient {

    private final RestClient restClient;
    private final CodefTokenClient tokenClient;
    private final ObjectMapper objectMapper;

    public CodefClient(
            @Qualifier("codefApiRestClient") RestClient restClient,
            CodefTokenClient tokenClient,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.tokenClient = tokenClient;
        this.objectMapper = objectMapper;
    }

    JsonNode post(String path, Object requestBody) {
        return post(path, requestBody, false);
    }

    JsonNode decode(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(responseBody);
        } catch (JacksonException ignored) {
            try {
                return objectMapper.readTree(URLDecoder.decode(responseBody, StandardCharsets.UTF_8));
            } catch (JacksonException | IllegalArgumentException exception) {
                throw failure(FinanceCollectionFailureCode.UPSTREAM_UNAVAILABLE, true,
                        "CODEF 응답을 확인할 수 없습니다.");
            }
        }
    }

    private JsonNode post(String path, Object requestBody, boolean retried) {
        try {
            String rawResponse = restClient.post()
                    .uri(path)
                    .headers(headers -> headers.setBearerAuth(tokenClient.getAccessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(UriUtils.encode(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                    .retrieve()
                    .body(String.class);
            return decode(rawResponse);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 401 && !retried) {
                tokenClient.clearCache();
                return post(path, requestBody, true);
            }
            throw new CodefHttpException(exception.getResponseBodyAsString(), exception);
        } catch (FinanceCollectionException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw failure(FinanceCollectionFailureCode.UPSTREAM_UNAVAILABLE, true,
                    "CODEF 금융기관과 통신할 수 없습니다.");
        } catch (JacksonException exception) {
            throw failure(FinanceCollectionFailureCode.INTERNAL_ERROR, false,
                    "CODEF 요청을 생성할 수 없습니다.");
        }
    }

    private FinanceCollectionException failure(
            FinanceCollectionFailureCode code,
            boolean retryable,
            String message
    ) {
        return new FinanceCollectionException(code, retryable, message);
    }

    static final class CodefHttpException extends RuntimeException {
        private final String responseBody;

        CodefHttpException(String responseBody, Throwable cause) {
            super("CODEF HTTP request failed", cause);
            this.responseBody = responseBody;
        }

        String responseBody() {
            return responseBody;
        }
    }
}
