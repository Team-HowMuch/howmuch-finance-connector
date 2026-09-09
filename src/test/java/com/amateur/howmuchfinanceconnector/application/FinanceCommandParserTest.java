package com.amateur.howmuchfinanceconnector.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FinanceCommandParserTest {

    private final FinanceCommandParser parser = new FinanceCommandParser(new ObjectMapper());

    @Test
    void parsesPublishedCommandAndRejectsUnknownContractValue() throws IOException {
        String payload = resource("examples/sync-account-transactions.json");

        assertEquals(LocalDate.of(2026, 9, 1), parser.parse(payload).fromDate());
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(payload.replace("SyncAccountTransactions", "UnknownMessage")));
    }

    private String resource(String path) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            if (input == null) throw new IllegalArgumentException("missing resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
