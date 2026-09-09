package com.amateur.howmuchfinanceconnector.adapter.in.kafka;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinanceSyncCommandParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FinanceSyncCommandParser parser = new FinanceSyncCommandParser(
            objectMapper,
            Clock.fixed(Instant.parse("2026-09-09T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void parsesPublishedRc2Command() throws IOException {
        String payload = example();

        var command = parser.parse("22222222-2222-4222-8222-222222222222", payload);

        assertThat(command.syncVersion()).isEqualTo(1);
        assertThat(command.payloadHash()).hasSize(64);
        assertThat(command.commandHash()).hasSize(64);
    }

    @Test
    void rejectsWrongKafkaKeyAndOutOfRangeDate() throws IOException {
        String payload = example();
        ObjectNode root = (ObjectNode) objectMapper.readTree(payload);
        ((ObjectNode) root.path("data")).put("fromDate", "2026-08-01");

        assertThatThrownBy(() -> parser.parse("00000000-0000-4000-8000-000000000000", payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Kafka key");
        assertThatThrownBy(() -> parser.parse(
                "22222222-2222-4222-8222-222222222222", objectMapper.writeValueAsString(root)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("날짜 범위");
    }

    private String example() throws IOException {
        try (var input = getClass().getClassLoader()
                .getResourceAsStream("examples/sync-account-transactions.json")) {
            if (input == null) {
                throw new IllegalStateException("Missing command example");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
