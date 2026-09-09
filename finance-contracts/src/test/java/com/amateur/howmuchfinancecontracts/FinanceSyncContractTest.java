package com.amateur.howmuchfinancecontracts;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class FinanceSyncContractTest {

    private static final List<String> EXAMPLES = List.of(
            "sync-account-transactions.json",
            "sync-accepted.json",
            "transactions-chunk-collected.json",
            "transactions-collection-completed.json",
            "transactions-sync-failed.json"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Schema schema = loadSchema();

    @Test
    void everyPublishedExampleMatchesVersionOneSchema() throws IOException {
        for (String example : EXAMPLES) {
            assertTrue(schema.validate(readJson("examples/" + example)).isEmpty(), example);
        }
    }

    @Test
    void requiredMetadataAndKnownMessageTypeAreEnforced() throws IOException {
        JsonNode missingJobId = readJson("examples/sync-account-transactions.json").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) missingJobId).remove("syncJobId");

        JsonNode unknownType = readJson("examples/sync-account-transactions.json").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) unknownType).put("messageType", "UnknownMessage");

        assertFalse(schema.validate(missingJobId).isEmpty());
        assertFalse(schema.validate(unknownType).isEmpty());
    }

    private Schema loadSchema() {
        return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(resource("schemas/finance-sync-message-v1.schema.json"));
    }

    private JsonNode readJson(String path) throws IOException {
        try (InputStream input = resource(path)) {
            return objectMapper.readTree(input);
        }
    }

    private InputStream resource(String path) {
        InputStream input = getClass().getClassLoader().getResourceAsStream(path);
        if (input == null) {
            throw new IllegalArgumentException("Missing classpath resource: " + path);
        }
        return input;
    }
}
