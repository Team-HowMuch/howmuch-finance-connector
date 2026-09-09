package com.amateur.howmuchfinanceconnector;

import com.amateur.howmuchfinanceconnector.adapter.in.kafka.FinanceSyncCommandParser;
import com.amateur.howmuchfinanceconnector.application.port.in.sync.AcceptFinanceSyncCommandUseCase;
import com.amateur.howmuchfinanceconnector.application.port.in.sync.CollectNextFinanceExecutionUseCase;
import com.amateur.howmuchfinanceconnector.application.port.in.sync.PublishNextFinanceEventUseCase;
import com.amateur.howmuchfinanceconnector.application.port.out.sync.BankTransactionCollector;
import com.amateur.howmuchfinanceconnector.application.port.out.sync.FinanceCollectionExecutionStore;
import com.amateur.howmuchfinanceconnector.application.sync.FinanceCommandRejectedException;
import com.amateur.howmuchfinanceconnector.application.sync.FinanceCollectionException;
import com.amateur.howmuchfinanceconnector.domain.sync.CollectedBankTransaction;
import com.amateur.howmuchfinanceconnector.domain.sync.FinanceCollectionFailureCode;
import com.amateur.howmuchfinanceconnector.domain.sync.FinanceTransactionDirection;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.kafka.core.KafkaTemplate;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FinanceCommandIntakeIntegrationTest.TestCollectionConfig.class)
class FinanceCommandIntakeIntegrationTest {

    private static final String TOKEN = "test-service-token";
    private static final UUID CONNECTION_REF = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID ACCOUNT_REF = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID SECOND_ACCOUNT_REF = UUID.fromString("77777777-7777-4777-8777-777777777777");

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

    @Autowired
    private FinanceSyncCommandParser parser;

    @Autowired
    private AcceptFinanceSyncCommandUseCase commandUseCase;

    @Autowired
    private FinanceCollectionExecutionStore collectionStore;

    @Autowired
    private CollectNextFinanceExecutionUseCase collectionWorker;

    @Autowired
    private PublishNextFinanceEventUseCase eventRelay;

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
        assertThat(putRawConnection(CONNECTION_REF, """
                {"connectedId":"id","organizationCode":"0088","accounts":[null]}
                """, TOKEN).statusCode()).isEqualTo(400);

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

        String competing = mutateCommand(command,
                "66666666-6666-4666-8666-666666666666",
                "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                ACCOUNT_REF,
                2,
                1);
        assertThatThrownBy(() -> commandUseCase.accept(parser.parse(ACCOUNT_REF.toString(), competing)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("finance_command_inbox")).isEqualTo(2);

        HttpResponse<String> changed = putConnection(CONNECTION_REF, SECOND_ACCOUNT_REF, TOKEN);
        assertThat(changed.body()).contains("\"connectionVersion\":2", "\"changed\":true");
        assertThat(active(ACCOUNT_REF)).isFalse();
        assertThat(active(SECOND_ACCOUNT_REF)).isTrue();

        UUID otherConnection = UUID.fromString("88888888-8888-4888-8888-888888888888");
        HttpResponse<String> conflict = putConnection(otherConnection, SECOND_ACCOUNT_REF, TOKEN);
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(conflict.body()).contains("FINANCE_CONNECTION_ACCOUNT_CONFLICT");

        assertThat(deleteConnection(CONNECTION_REF).statusCode()).isEqualTo(204);
        assertThat(jdbc.sql("SELECT active FROM financial_connections")
                .query(Boolean.class).single()).isFalse();
        assertThat(active(SECOND_ACCOUNT_REF)).isFalse();

        String revoked = mutateCommand(command,
                "99999999-9999-4999-8999-999999999999",
                "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
                SECOND_ACCOUNT_REF,
                1,
                2);
        assertThatThrownBy(() -> commandUseCase.accept(parser.parse(SECOND_ACCOUNT_REF.toString(), revoked)))
                .isInstanceOf(FinanceCommandRejectedException.class);
        assertThat(count("finance_command_inbox")).isEqualTo(2);
    }

    @Test
    void storesRc2ChunksAndPublishesThemWithAccountReferenceKey() throws Exception {
        assertThat(putConnection(TOKEN).statusCode()).isEqualTo(200);
        UUID messageId = UUID.randomUUID();
        UUID syncJobId = UUID.randomUUID();
        String command = commandPayload(messageId, syncJobId, 1);
        commandUseCase.accept(parser.parse(ACCOUNT_REF.toString(), command));
        var task = collectionStore.claimNextExecution(LocalDateTime.now(ZoneOffset.UTC)).orElseThrow();
        List<CollectedBankTransaction> transactions = IntStream.range(0, 101)
                .mapToObj(index -> new CollectedBankTransaction(
                        "provider-transaction-" + index,
                        LocalDateTime.of(2026, 9, 9, 0, 0).plusSeconds(index),
                        FinanceTransactionDirection.EXPENSE,
                        BigDecimal.valueOf(index + 1L),
                        "KRW",
                        BigDecimal.valueOf(100_000L - index),
                        "거래 " + index))
                .toList();

        collectionStore.complete(task, transactions, LocalDateTime.now(ZoneOffset.UTC));

        List<String> payloads = jdbc.sql("""
                        SELECT payload::text FROM finance_event_outbox
                        WHERE sync_job_id = :syncJobId AND event_type <> 'SyncAccepted'
                        ORDER BY created_at, message_id
                        """)
                .param("syncJobId", syncJobId)
                .query(String.class)
                .list();
        assertThat(payloads).hasSize(3);
        payloads.forEach(payload -> assertThat(schema.validate(payload, InputFormat.JSON,
                context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)))).isEmpty());
        ObjectNode firstChunk = (ObjectNode) objectMapper.readTree(payloads.getFirst());
        ObjectNode secondChunk = (ObjectNode) objectMapper.readTree(payloads.get(1));
        ObjectNode completed = (ObjectNode) objectMapper.readTree(payloads.getLast());
        assertThat(firstChunk.path("data").path("items")).hasSize(100);
        assertThat(secondChunk.path("data").path("items")).hasSize(1);
        assertThat(completed.path("data").path("totalChunks").intValue()).isEqualTo(2);
        assertThat(completed.path("data").path("totalItems").intValue()).isEqualTo(101);
        assertThat(firstChunk.path("data").path("items").get(0).path("transactionKey").stringValue())
                .isEqualTo(sha256(ACCOUNT_REF + "provider-transaction-0"));
        assertThat(payloads.getFirst().getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(256 * 1024);
        assertThat(jdbc.sql("SELECT status FROM collection_executions WHERE sync_job_id = :syncJobId")
                .param("syncJobId", syncJobId).query(String.class).single()).isEqualTo("SUCCEEDED");

        while (eventRelay.publishNext()) {
            // Drain the outbox for this isolated test database.
        }
        assertThat(kafkaEvents(syncJobId, 4)).hasSize(4).allSatisfy(record -> {
            assertThat(record.key()).isEqualTo(ACCOUNT_REF.toString());
            assertThat(record.value().getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(256 * 1024);
        });
    }

    @Test
    void splitsChunksBeforeKafkaPayloadExceedsByteLimit() throws Exception {
        assertThat(putConnection(TOKEN).statusCode()).isEqualTo(200);
        UUID syncJobId = UUID.randomUUID();
        commandUseCase.accept(parser.parse(ACCOUNT_REF.toString(), commandPayload(UUID.randomUUID(), syncJobId, 1)));
        var task = collectionStore.claimNextExecution(LocalDateTime.now(ZoneOffset.UTC)).orElseThrow();
        BigDecimal largeInteger = new BigDecimal("1" + "0".repeat(9_999));
        List<CollectedBankTransaction> transactions = IntStream.range(0, 20)
                .mapToObj(index -> new CollectedBankTransaction(
                        "large-provider-transaction-" + index,
                        LocalDateTime.of(2026, 9, 9, 0, 0).plusSeconds(index),
                        FinanceTransactionDirection.INCOME,
                        largeInteger,
                        "KRW",
                        largeInteger,
                        "대용량 거래"))
                .toList();

        collectionStore.complete(task, transactions, LocalDateTime.now(ZoneOffset.UTC));

        int chunks = jdbc.sql("""
                        SELECT COUNT(*) FROM finance_event_outbox
                        WHERE sync_job_id = :syncJobId AND event_type = 'TransactionsChunkCollected'
                        """).param("syncJobId", syncJobId).query(Integer.class).single();
        assertThat(chunks).isGreaterThan(1);
        while (eventRelay.publishNext()) {
            // Drain the outbox for this isolated test database.
        }
        assertThat(kafkaEvents(syncJobId, chunks + 2)).hasSize(chunks + 2).allSatisfy(record ->
                assertThat(record.value().getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(256 * 1024));
    }

    @Test
    void storesEmptyCollectionAsCompletedWithoutChunks() throws Exception {
        assertThat(putConnection(TOKEN).statusCode()).isEqualTo(200);
        UUID syncJobId = UUID.randomUUID();
        commandUseCase.accept(parser.parse(ACCOUNT_REF.toString(), commandPayload(UUID.randomUUID(), syncJobId, 1)));
        assertThat(collectionWorker.collectNext()).isTrue();

        assertThat(jdbc.sql("""
                        SELECT event_type FROM finance_event_outbox
                        WHERE sync_job_id = :syncJobId AND event_type <> 'SyncAccepted'
                        """)
                .param("syncJobId", syncJobId).query(String.class).list())
                .containsExactly("TransactionsCollectionCompleted");
        assertThat(jdbc.sql("""
                        SELECT total_chunk_count || ':' || total_transaction_count
                        FROM collection_executions WHERE sync_job_id = :syncJobId
                        """)
                .param("syncJobId", syncJobId).query(String.class).single()).isEqualTo("0:0");
    }

    @Test
    void rejectsCompletionAfterConnectionIsRevoked() throws Exception {
        assertThat(putConnection(TOKEN).statusCode()).isEqualTo(200);
        UUID syncJobId = UUID.randomUUID();
        commandUseCase.accept(parser.parse(ACCOUNT_REF.toString(), commandPayload(UUID.randomUUID(), syncJobId, 1)));
        var task = collectionStore.claimNextExecution(LocalDateTime.now(ZoneOffset.UTC)).orElseThrow();
        assertThat(deleteConnection(CONNECTION_REF).statusCode()).isEqualTo(204);

        assertThatThrownBy(() -> collectionStore.complete(task, List.of(new CollectedBankTransaction(
                "provider-id", LocalDateTime.now(ZoneOffset.UTC), FinanceTransactionDirection.INCOME,
                BigDecimal.ONE, "KRW", BigDecimal.TEN, "입금")), LocalDateTime.now(ZoneOffset.UTC)))
                .isInstanceOfSatisfying(FinanceCollectionException.class,
                        exception -> assertThat(exception.failureCode())
                                .isEqualTo(FinanceCollectionFailureCode.CONNECTION_REVOKED));
        assertThat(jdbc.sql("""
                        SELECT COUNT(*) FROM finance_event_outbox
                        WHERE sync_job_id = :syncJobId AND event_type <> 'SyncAccepted'
                        """).param("syncJobId", syncJobId).query(Integer.class).single()).isZero();
    }

    private HttpResponse<String> putConnection(String token) throws Exception {
        return putConnection(CONNECTION_REF, ACCOUNT_REF, token);
    }

    private HttpResponse<String> putConnection(UUID connectionRef, UUID accountRef, String token) throws Exception {
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
                """.formatted(accountRef);
        return putRawConnection(connectionRef, body, token);
    }

    private HttpResponse<String> putRawConnection(UUID connectionRef, String body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri("/internal/v1/connections/" + connectionRef))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> deleteConnection(UUID connectionRef) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/internal/v1/connections/" + connectionRef))
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

    private String commandPayload(UUID messageId, UUID syncJobId, long syncVersion) throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(commandPayload());
        root.put("messageId", messageId.toString());
        root.put("syncJobId", syncJobId.toString());
        root.put("syncVersion", syncVersion);
        return objectMapper.writeValueAsString(root);
    }

    private String commandPayloadWithNewMessageId(String command) {
        ObjectNode root = (ObjectNode) objectMapper.readTree(command);
        root.put("messageId", "55555555-5555-4555-8555-555555555555");
        return objectMapper.writeValueAsString(root);
    }

    private String mutateCommand(
            String command,
            String messageId,
            String syncJobId,
            UUID accountRef,
            long syncVersion,
            long connectionVersion
    ) {
        ObjectNode root = (ObjectNode) objectMapper.readTree(command);
        root.put("messageId", messageId);
        root.put("syncJobId", syncJobId);
        root.put("accountRef", accountRef.toString());
        root.put("syncVersion", syncVersion);
        root.put("connectionVersion", connectionVersion);
        return objectMapper.writeValueAsString(root);
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private int count(String table) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table).query(Integer.class).single();
    }

    private boolean active(UUID accountRef) {
        return jdbc.sql("SELECT active FROM connected_accounts WHERE account_ref = :accountRef")
                .param("accountRef", accountRef)
                .query(Boolean.class)
                .single();
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

    private List<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>> kafkaEvents(
            UUID syncJobId,
            int expected
    ) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "finance-event-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>> matching = new ArrayList<>();
        try (var consumer = new KafkaConsumer<String, String>(properties)) {
            consumer.subscribe(List.of("finance.sync.event.v1"));
            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            while (matching.size() < expected && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (objectMapper.readTree(record.value()).path("syncJobId").asText().equals(syncJobId.toString())) {
                        matching.add(record);
                    }
                });
            }
        }
        return matching;
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestCollectionConfig {
        @Bean
        @Primary
        BankTransactionCollector bankTransactionCollector() {
            return task -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                return List.of();
            };
        }
    }
}
