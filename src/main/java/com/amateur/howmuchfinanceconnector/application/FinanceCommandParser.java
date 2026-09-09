package com.amateur.howmuchfinanceconnector.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FinanceCommandParser {

    private final ObjectMapper objectMapper;
    private final Schema schema;

    public FinanceCommandParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        InputStream input = getClass().getClassLoader().getResourceAsStream("schemas/finance-sync-message-v1.schema.json");
        if (input == null) {
            throw new IllegalStateException("finance contract schema is missing");
        }
        this.schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(input);
    }

    public FinanceSyncCommand parse(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            var errors = schema.validate(root);
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException("finance command violates schema: " + errors.iterator().next().getMessage());
            }
            if (!"SyncAccountTransactions".equals(text(root, "messageType")) || root.path("schemaVersion").asInt() != 1) {
                throw new IllegalArgumentException("unsupported finance command");
            }
            JsonNode data = root.path("data");
            LocalDate fromDate = LocalDate.parse(text(data, "fromDate"));
            LocalDate toDate = LocalDate.parse(text(data, "toDate"));
            if (fromDate.isAfter(toDate) || ChronoUnit.DAYS.between(fromDate, toDate) >= 31) {
                throw new IllegalArgumentException("finance command date range must be at most 31 days");
            }
            return new FinanceSyncCommand(
                    UUID.fromString(text(root, "messageId")),
                    Instant.parse(text(root, "occurredAt")),
                    UUID.fromString(text(root, "syncJobId")),
                    UUID.fromString(text(root, "accountRef")),
                    root.path("syncVersion").asLong(),
                    root.path("connectionVersion").asLong(),
                    root.path("traceId").isNull() ? null : text(root, "traceId"),
                    UUID.fromString(text(data, "connectionRef")),
                    fromDate,
                    toDate,
                    Instant.parse(text(data, "deadlineAt"))
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("finance command is not valid JSON", exception);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("missing finance command field: " + field);
        }
        return value.textValue();
    }
}
