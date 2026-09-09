package com.amateur.howmuchfinanceconnector.adapter.out.codef;

import com.amateur.howmuchfinanceconnector.application.port.out.connection.ConnectionSecretCipher;
import com.amateur.howmuchfinanceconnector.application.port.out.sync.BankTransactionCollector;
import com.amateur.howmuchfinanceconnector.application.port.out.sync.FinanceCollectionCredentialStore;
import com.amateur.howmuchfinanceconnector.application.sync.FinanceCollectionException;
import com.amateur.howmuchfinanceconnector.domain.sync.CollectedBankTransaction;
import com.amateur.howmuchfinanceconnector.domain.sync.FinanceCollectionFailureCode;
import com.amateur.howmuchfinanceconnector.domain.sync.FinanceCollectionTask;
import com.amateur.howmuchfinanceconnector.domain.sync.FinanceTransactionDirection;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CodefBankTransactionCollector implements BankTransactionCollector {

    private static final String SUCCESS = "CF-00000";
    private static final String TRANSACTION_LIST_PATH = "/v1/kr/bank/p/account/transaction-list";
    private static final DateTimeFormatter CODEF_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter CODEF_TIME = DateTimeFormatter.ofPattern("HHmmss");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final CodefClient client;
    private final FinanceCollectionCredentialStore credentials;
    private final ConnectionSecretCipher cipher;

    public CodefBankTransactionCollector(
            CodefClient client,
            FinanceCollectionCredentialStore credentials,
            ConnectionSecretCipher cipher
    ) {
        this.client = client;
        this.credentials = credentials;
        this.cipher = cipher;
    }

    @Override
    public List<CollectedBankTransaction> collect(FinanceCollectionTask task) {
        var credential = credentials.findActive(
                        task.connectionRef(), task.accountRef(), task.connectionVersion())
                .orElseThrow(() -> failure(FinanceCollectionFailureCode.CONNECTION_REVOKED, false,
                        "금융 연결이 해제되었거나 변경되었습니다."));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("connectedId", cipher.decrypt(credential.connectedIdCiphertext()));
        payload.put("organization", credential.organizationCode());
        payload.put("account", cipher.decrypt(credential.providerAccountRefCiphertext()));
        payload.put("startDate", CODEF_DATE.format(task.fromDate()));
        payload.put("endDate", CODEF_DATE.format(task.toDate()));
        payload.put("orderBy", "1");
        payload.put("inquiryType", "1");

        JsonNode response = post(payload);
        List<CollectedBankTransaction> transactions = new ArrayList<>();
        for (JsonNode item : values(data(response).path("resTrHistoryList"))) {
            CollectedBankTransaction transaction = transaction(item);
            if (transaction != null) {
                transactions.add(transaction);
            }
        }
        return List.copyOf(transactions);
    }

    private JsonNode post(Map<String, Object> payload) {
        try {
            JsonNode response = client.post(TRANSACTION_LIST_PATH, payload);
            ensureSuccess(response);
            return response;
        } catch (CodefClient.CodefHttpException exception) {
            String code = text(client.decode(exception.responseBody()).path("result"), "code");
            throw mapped(code);
        }
    }

    private void ensureSuccess(JsonNode response) {
        if (containsAdditionalAuth(response)) {
            throw failure(FinanceCollectionFailureCode.REAUTH_REQUIRED, false,
                    "금융기관 추가 인증이 필요합니다.");
        }
        String code = text(response.path("result"), "code");
        if (!SUCCESS.equals(code)) {
            throw mapped(code);
        }
    }

    private FinanceCollectionException mapped(String code) {
        return switch (code == null ? "" : code) {
            case "CF-03002", "CF-12001" -> failure(FinanceCollectionFailureCode.REAUTH_REQUIRED, false,
                    "금융기관 추가 인증이 필요합니다.");
            case "CF-04000" -> failure(FinanceCollectionFailureCode.INVALID_REQUEST, false,
                    "해당 계좌의 거래 조회를 지원하지 않습니다.");
            default -> failure(FinanceCollectionFailureCode.UPSTREAM_UNAVAILABLE, true,
                    "금융기관과 통신할 수 없습니다. 잠시 후 다시 시도해주세요.");
        };
    }

    private CollectedBankTransaction transaction(JsonNode item) {
        LocalDateTime bookedAt = bookedAt(item);
        BigDecimal out = positiveAmount(text(item, "resAccountOut"));
        BigDecimal in = positiveAmount(text(item, "resAccountIn"));
        FinanceTransactionDirection direction = out != null ? FinanceTransactionDirection.EXPENSE
                : in != null ? FinanceTransactionDirection.INCOME : null;
        BigDecimal amount = out != null ? out : in;
        if (bookedAt == null || direction == null || amount.signum() <= 0) {
            return null;
        }
        return new CollectedBankTransaction(
                null,
                bookedAt,
                direction,
                amount,
                "KRW",
                signedAmount(text(item, "resAfterTranBalance")),
                description(item));
    }

    private LocalDateTime bookedAt(JsonNode item) {
        String date = digits(text(item, "resAccountTrDate"));
        String time = digits(text(item, "resAccountTrTime"));
        if (date == null || date.length() != 8 || time == null || time.length() != 6) {
            return null;
        }
        try {
            return LocalDateTime.of(LocalDate.parse(date, CODEF_DATE), LocalTime.parse(time, CODEF_TIME))
                    .atZone(SEOUL)
                    .withZoneSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (DateTimeException ignored) {
            return null;
        }
    }

    private String description(JsonNode item) {
        List<String> descriptions = new ArrayList<>();
        for (String field : List.of("resAccountDesc1", "resAccountDesc2", "resAccountDesc3", "resAccountDesc4")) {
            String value = text(item, field);
            if (value != null && !descriptions.contains(value)) {
                descriptions.add(value);
            }
        }
        if (descriptions.isEmpty() && text(item, "resAccountDesc") != null) {
            descriptions.add(text(item, "resAccountDesc"));
        }
        String result = String.join(" ", descriptions);
        return result.length() <= 500 ? result : result.substring(0, 500);
    }

    private BigDecimal positiveAmount(String value) {
        BigDecimal amount = signedAmount(value);
        return amount != null && amount.signum() > 0 ? amount : null;
    }

    private BigDecimal signedAmount(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean containsAdditionalAuth(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (node.isObject() && node.path("continue2Way").asBoolean(false)) {
            return true;
        }
        for (JsonNode child : node) {
            if (containsAdditionalAuth(child)) {
                return true;
            }
        }
        return false;
    }

    private JsonNode data(JsonNode response) {
        JsonNode data = response.path("data");
        return data.isMissingNode() || data.isNull() ? response : data;
    }

    private List<JsonNode> values(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            List<JsonNode> values = new ArrayList<>();
            node.forEach(values::add);
            return values;
        }
        return node.isObject() ? List.of(node) : List.of();
    }

    private String text(JsonNode node, String field) {
        String value = node == null ? null : node.path(field).asText(null);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String digits(String value) {
        return value == null ? null : value.replaceAll("\\D", "");
    }

    private FinanceCollectionException failure(
            FinanceCollectionFailureCode code,
            boolean retryable,
            String message
    ) {
        return new FinanceCollectionException(code, retryable, message);
    }
}
