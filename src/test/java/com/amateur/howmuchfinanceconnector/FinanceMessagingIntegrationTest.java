package com.amateur.howmuchfinanceconnector;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.amateur.howmuchfinanceconnector.adapter.in.schedule.CollectionWorker;
import com.amateur.howmuchfinanceconnector.adapter.out.kafka.ConnectorOutboxRelay;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.CollectionExecutionJpaRepository;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.ConnectedAccountJpaEntity;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.ConnectedAccountJpaRepository;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.FinancialConnectionJpaEntity;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.FinancialConnectionJpaRepository;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.OutboxEventJpaRepository;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.ProcessedMessageJpaRepository;
import com.amateur.howmuchfinanceconnector.application.MessagingRetentionService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "finance.messaging.enabled=true",
        "connector.internal.service-token=integration-test-token"
})
class FinanceMessagingIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.2"));

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired FinancialConnectionJpaRepository connections;
    @Autowired ConnectedAccountJpaRepository accounts;
    @Autowired CollectionExecutionJpaRepository executions;
    @Autowired ProcessedMessageJpaRepository processed;
    @Autowired OutboxEventJpaRepository outbox;

    @MockitoBean CollectionWorker collectionWorker;
    @MockitoBean ConnectorOutboxRelay outboxRelay;
    @MockitoBean MessagingRetentionService retentionService;

    private UUID connectionRef;
    private UUID accountRef;

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
        processed.deleteAll();
        executions.deleteAll();
        accounts.deleteAll();
        connections.deleteAll();

        connectionRef = UUID.randomUUID();
        accountRef = UUID.randomUUID();
        Instant now = Instant.now();
        connections.save(new FinancialConnectionJpaEntity(
                connectionRef, 1, "CODEF", "BANK", "0004", "connected-id", "a".repeat(64), now
        ));
        accounts.save(new ConnectedAccountJpaEntity(accountRef, connectionRef, "provider-account", now));
    }

    @Test
    void duplicateKafkaDeliveryCreatesOneExecutionAndOneOutboxEvent() throws Exception {
        String command = command(UUID.randomUUID(), UUID.randomUUID());

        kafka.send("finance.sync.command.v1", accountRef.toString(), command).get();
        kafka.send("finance.sync.command.v1", accountRef.toString(), command).get();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertEquals(1, executions.count());
            assertEquals(1, processed.count());
            assertEquals(1, outbox.count());
        });
    }

    @Test
    void malformedJsonIsPublishedToTheRealDlt() throws Exception {
        String dlt = "finance.sync.command.v1.dlt";
        var properties = KafkaTestUtils.consumerProps(
                KAFKA.getBootstrapServers(), "dlt-verification-" + UUID.randomUUID(), "false"
        );
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                properties, new StringDeserializer(), new StringDeserializer()
        ).createConsumer()) {
            consumer.subscribe(List.of(dlt));
            consumer.poll(Duration.ofMillis(100));

            kafka.send("finance.sync.command.v1", accountRef.toString(), "{not-json").get();

            assertEquals("{not-json", KafkaTestUtils.getSingleRecord(consumer, dlt, Duration.ofSeconds(15)).value());
        }
        assertEquals(0, executions.count());
    }

    private String command(UUID messageId, UUID syncJobId) {
        LocalDate today = LocalDate.now();
        return """
                {
                  "messageId":"%s",
                  "messageType":"SyncAccountTransactions",
                  "schemaVersion":1,
                  "occurredAt":"%s",
                  "syncJobId":"%s",
                  "accountRef":"%s",
                  "syncVersion":1,
                  "connectionVersion":1,
                  "causationId":null,
                  "traceId":null,
                  "data":{
                    "connectionRef":"%s",
                    "fromDate":"%s",
                    "toDate":"%s",
                    "timeZone":"Asia/Seoul",
                    "reason":"USER_REFRESH",
                    "deadlineAt":"%s"
                  }
                }
                """.formatted(
                messageId, Instant.now(), syncJobId, accountRef, connectionRef,
                today.minusDays(1), today, Instant.now().plus(Duration.ofMinutes(10))
        );
    }
}
