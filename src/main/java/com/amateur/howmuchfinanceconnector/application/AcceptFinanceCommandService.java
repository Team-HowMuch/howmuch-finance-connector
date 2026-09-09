package com.amateur.howmuchfinanceconnector.application;

import com.amateur.howmuchfinanceconnector.adapter.out.persistence.CollectionExecutionJpaEntity;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.CollectionExecutionJpaRepository;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.ConnectedAccountJpaRepository;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.FinancialConnectionJpaRepository;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.OutboxEventJpaEntity;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.OutboxEventJpaRepository;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.ProcessedMessageJpaEntity;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.ProcessedMessageJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcceptFinanceCommandService {

    private static final String CONSUMER = "finance-connector-sync-v1";

    private final FinanceCommandParser parser;
    private final ObjectMapper objectMapper;
    private final ProcessedMessageJpaRepository processedRepository;
    private final CollectionExecutionJpaRepository executionRepository;
    private final FinancialConnectionJpaRepository connectionRepository;
    private final ConnectedAccountJpaRepository accountRepository;
    private final OutboxEventJpaRepository outboxRepository;
    private final String eventTopic;

    public AcceptFinanceCommandService(
            FinanceCommandParser parser,
            ObjectMapper objectMapper,
            ProcessedMessageJpaRepository processedRepository,
            CollectionExecutionJpaRepository executionRepository,
            FinancialConnectionJpaRepository connectionRepository,
            ConnectedAccountJpaRepository accountRepository,
            OutboxEventJpaRepository outboxRepository,
            @Value("${finance.kafka.event-topic:finance.sync.event.v1}") String eventTopic
    ) {
        this.parser = parser;
        this.objectMapper = objectMapper;
        this.processedRepository = processedRepository;
        this.executionRepository = executionRepository;
        this.connectionRepository = connectionRepository;
        this.accountRepository = accountRepository;
        this.outboxRepository = outboxRepository;
        this.eventTopic = eventTopic;
    }

    @Transactional
    public void accept(String payload) {
        FinanceSyncCommand command = parser.parse(payload);
        String payloadHash = MessageHash.sha256(payload);
        String requestHash = MessageHash.sha256(command.requestIdentity());
        ProcessedMessageJpaEntity processed = processedRepository
                .findByConsumerNameAndMessageId(CONSUMER, command.messageId())
                .orElse(null);
        if (processed != null) {
            if (!processed.getPayloadHash().equals(payloadHash)) {
                throw new IllegalStateException("messageId payload conflict");
            }
            return;
        }

        CollectionExecutionJpaEntity existing = executionRepository.findById(command.syncJobId()).orElse(null);
        if (existing != null) {
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new IllegalStateException("syncJobId payload conflict");
            }
            processedRepository.save(new ProcessedMessageJpaEntity(CONSUMER, command.messageId(), payloadHash, Instant.now()));
            return;
        }

        var connection = connectionRepository.findById(command.connectionRef())
                .filter(value -> value.isActive() && value.getConnectionVersion() == command.connectionVersion())
                .orElseThrow(() -> new IllegalStateException("connection is not active for command version"));
        accountRepository.findByAccountRefAndConnectionRefAndActiveTrue(command.accountRef(), connection.getConnectionRef())
                .orElseThrow(() -> new IllegalStateException("account is not active for connection"));

        Instant now = Instant.now();
        executionRepository.save(new CollectionExecutionJpaEntity(command, requestHash, now));
        outboxRepository.save(acceptedEvent(command, now));
        processedRepository.save(new ProcessedMessageJpaEntity(CONSUMER, command.messageId(), payloadHash, now));
    }

    private OutboxEventJpaEntity acceptedEvent(FinanceSyncCommand command, Instant now) {
        UUID messageId = UUID.randomUUID();
        ObjectNode root = objectMapper.createObjectNode();
        root.put("messageId", messageId.toString());
        root.put("messageType", "SyncAccepted");
        root.put("schemaVersion", 1);
        root.put("occurredAt", now.toString());
        root.put("syncJobId", command.syncJobId().toString());
        root.put("accountRef", command.accountRef().toString());
        root.put("syncVersion", command.syncVersion());
        root.put("connectionVersion", command.connectionVersion());
        root.put("causationId", command.messageId().toString());
        if (command.traceId() == null) root.putNull("traceId"); else root.put("traceId", command.traceId());
        root.putObject("data").put("acceptedAt", now.toString());
        try {
            return new OutboxEventJpaEntity(
                    messageId,
                    command.syncJobId(),
                    eventTopic,
                    command.accountRef().toString(),
                    objectMapper.writeValueAsString(root),
                    now
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("accepted event cannot be serialized", exception);
        }
    }
}
