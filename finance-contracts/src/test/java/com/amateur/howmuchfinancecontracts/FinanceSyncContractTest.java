package com.amateur.howmuchfinancecontracts;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinanceSyncContractTest {

    private static final List<String> EXAMPLES = List.of(
            "sync-account-transactions.json",
            "sync-accepted.json",
            "transactions-chunk-collected.json",
            "transactions-collection-completed.json",
            "transactions-collection-empty.json",
            "transactions-sync-failed.json"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Schema schema = SchemaRegistry
            .withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(SchemaLocation.of("classpath:schemas/finance-sync-message-v1.schema.json"));

    @Test
    void acceptsEveryPublishedExample() throws IOException {
        for (String example : EXAMPLES) {
            assertTrue(validate(read(example)).isEmpty(), example);
        }
    }

    @Test
    void rejectsMissingMetadataUnsupportedVersionAndNewEnum() throws IOException {
        ObjectNode missingMessageId = read("sync-account-transactions.json").deepCopy();
        missingMessageId.remove("messageId");

        ObjectNode unsupportedVersion = read("sync-account-transactions.json").deepCopy();
        unsupportedVersion.put("schemaVersion", 2);

        ObjectNode unknownDirection = read("transactions-chunk-collected.json").deepCopy();
        ((ObjectNode) unknownDirection.path("data").path("items").get(0))
                .put("direction", "TRANSFER");

        assertFalse(validate(missingMessageId).isEmpty());
        assertFalse(validate(unsupportedVersion).isEmpty());
        assertFalse(validate(unknownDirection).isEmpty());
    }

    @Test
    void requiresStableProviderIdentityAndDerivedTransactionKey() throws IOException {
        ObjectNode missingProviderId = read("transactions-chunk-collected.json").deepCopy();
        ((ObjectNode) missingProviderId.path("data").path("items").get(0))
                .remove("providerTransactionId");

        ObjectNode invalidKey = read("transactions-chunk-collected.json").deepCopy();
        ((ObjectNode) invalidKey.path("data").path("items").get(0))
                .put("transactionKey", "not-a-sha256-key");

        JsonNode published = read("transactions-chunk-collected.json");
        JsonNode transaction = published.path("data").path("items").get(0);
        String expectedKey = sha256(published.path("accountRef").stringValue()
                + transaction.path("providerTransactionId").stringValue());

        assertFalse(validate(missingProviderId).isEmpty());
        assertFalse(validate(invalidKey).isEmpty());
        assertEquals(expectedKey, transaction.path("transactionKey").stringValue());
    }

    @Test
    void rejectsChunkWithMoreThanOneHundredTransactions() throws IOException {
        ObjectNode chunk = read("transactions-chunk-collected.json").deepCopy();
        ArrayNode items = (ArrayNode) chunk.path("data").path("items");
        JsonNode item = items.get(0);
        while (items.size() <= 100) {
            items.add(item.deepCopy());
        }

        assertFalse(validate(chunk).isEmpty());
    }

    private List<com.networknt.schema.Error> validate(JsonNode node) {
        return schema.validate(objectMapper.writeValueAsString(node), InputFormat.JSON,
                context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)));
    }

    private ObjectNode read(String name) throws IOException {
        try (InputStream input = resource("examples/" + name)) {
            return (ObjectNode) objectMapper.readTree(input);
        }
    }

    private InputStream resource(String path) {
        InputStream input = getClass().getClassLoader().getResourceAsStream(path);
        if (input == null) {
            throw new IllegalArgumentException("Missing classpath resource: " + path);
        }
        return input;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
