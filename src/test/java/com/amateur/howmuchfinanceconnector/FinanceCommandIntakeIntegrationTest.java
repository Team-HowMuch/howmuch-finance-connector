package com.amateur.howmuchfinanceconnector;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FinanceCommandIntakeIntegrationTest {

    private static final String TOKEN = "test-service-token";
    private static final UUID CONNECTION_REF = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID ACCOUNT_REF = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))
            .withDatabaseName("howmuch_finance_connector_test")
            .withUsername("howmuch_connector_test")
            .withPassword("connector_test");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:4.2.1"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("finance.messaging.enabled", () -> true);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private KafkaTemplate<String, String> kafka;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Schema schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(SchemaLocation.of("classpath:schemas/finance-sync-message-v1.schema.json"));

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("DELETE FROM finance_event_outbox").update();
        jdbc.sql("DELETE FROM collection_executions").update();
        jdbc.sql("DELETE FROM finance_command_inbox").update();
        jdbc.sql("DELETE FROM connected_accounts").update();
        jdbc.sql("DELETE FROM financial_connections").update();
    }

    @Test
    void registersEncryptedConnectionAndAcceptsDuplicateCommandOnlyOnce() throws Exception {
        assertThat(putConnection(null).statusCode()).isEqualTo(401);

        HttpResponse<String> first = putConnection(TOKEN);
        HttpResponse<String> duplicate = putConnection(TOKEN);

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.body()).contains("\"connectionVersion\":1", "\"changed\":true");
        assertThat(duplicate.body()).contains("\"connectionVersion\":1", "\"changed\":false");
        assertThat(jdbc.sql("SELECT connected_id_ciphertext FROM financial_connections")
                .query(String.class).single()).startsWith("v1:").doesNotContain("test-connected-id");
        assertThat(jdbc.sql("SELECT provider_account_ref_ciphertext FROM connected_accounts")
                .query(String.class).single()).startsWith("v1:").doesNotContain("test-provider-account");

        String command = commandPayload();
        kafka.send("finance.sync.command.v1", ACCOUNT_REF.toString(), command).get();
        awaitCount("collection_executions", 1);
        kafka.send("finance.sync.command.v1", ACCOUNT_REF.toString(), command).get();
        kafka.send("finance.sync.command.v1", ACCOUNT_REF.toString(), commandPayloadWithNewMessageId(command)).get();
        awaitCount("finance_command_inbox", 2);

        assertThat(count("collection_executions")).isEqualTo(1);
        assertThat(count("finance_event_outbox")).isEqualTo(1);
        String acceptedPayload = jdbc.sql("SELECT payload::text FROM finance_event_outbox")
                .query(String.class).single();
        assertThat(schema.validate(acceptedPayload, InputFormat.JSON,
                context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)))).isEmpty();

        assertThat(deleteConnection().statusCode()).isEqualTo(204);
        assertThat(jdbc.sql("SELECT active FROM financial_connections")
                .query(Boolean.class).single()).isFalse();
        assertThat(jdbc.sql("SELECT active FROM connected_accounts")
                .query(Boolean.class).single()).isFalse();
    }

    private HttpResponse<String> putConnection(String token) throws Exception {
        String body = """
                {
                  "connectedId": "test-connected-id",
                  "organizationCode": "0088",
                  "accounts": [{
                    "accountRef": "%s",
                    "providerAccountRef": "test-provider-account",
                    "accountType": "DEPOSIT_TRUST",
                    "productCode": "11",
                    "currency": "KRW"
                  }]
                }
                """.formatted(ACCOUNT_REF);
        HttpRequest.Builder request = HttpRequest.newBuilder(uri("/internal/v1/connections/" + CONNECTION_REF))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> deleteConnection() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/internal/v1/connections/" + CONNECTION_REF))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + TOKEN)
                .DELETE()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String commandPayload() throws Exception {
        try (var input = getClass().getClassLoader()
                .getResourceAsStream("examples/sync-account-transactions.json")) {
            if (input == null) {
                throw new IllegalStateException("Missing command example");
            }
            ObjectNode root = (ObjectNode) objectMapper.readTree(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8));
            Instant now = Instant.now();
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
            root.put("occurredAt", now.toString());
            ObjectNode data = (ObjectNode) root.path("data");
            data.put("fromDate", today.minusDays(1).toString());
            data.put("toDate", today.toString());
            data.put("deadlineAt", now.plus(Duration.ofMinutes(10)).toString());
            return objectMapper.writeValueAsString(root);
        }
    }

    private String commandPayloadWithNewMessageId(String command) {
        ObjectNode root = (ObjectNode) objectMapper.readTree(command);
        root.put("messageId", "55555555-5555-4555-8555-555555555555");
        return objectMapper.writeValueAsString(root);
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private int count(String table) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table).query(Integer.class).single();
    }

    private void awaitCount(String table, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            if (count(table) == expected) {
                return;
            }
            Thread.sleep(100);
        }
        assertThat(count(table)).isEqualTo(expected);
    }
}
