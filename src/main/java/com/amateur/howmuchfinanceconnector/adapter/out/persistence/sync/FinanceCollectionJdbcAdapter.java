package com.amateur.howmuchfinanceconnector.adapter.out.persistence.sync;

import com.amateur.howmuchfinanceconnector.application.port.out.sync.FinanceCollectionCredentialStore;
import com.amateur.howmuchfinanceconnector.application.port.out.sync.FinanceCollectionExecutionStore;
import com.amateur.howmuchfinanceconnector.application.port.out.sync.FinanceEventOutboxStore;
import com.amateur.howmuchfinanceconnector.application.sync.FinanceCollectionException;
import com.amateur.howmuchfinanceconnector.domain.sync.CollectedBankTransaction;
import com.amateur.howmuchfinanceconnector.domain.sync.FinanceCollectionFailureCode;
import com.amateur.howmuchfinanceconnector.domain.sync.FinanceCollectionTask;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FinanceCollectionJdbcAdapter implements
        FinanceCollectionExecutionStore,
        FinanceCollectionCredentialStore,
        FinanceEventOutboxStore {

    private static final String EVENT_TOPIC = "finance.sync.event.v1";
    private static final int MAX_CHUNK_ITEMS = 100;
    private static final int MAX_CHUNK_BYTES = 256 * 1024;

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public FinanceCollectionJdbcAdapter(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public Optional<FinanceCollectionTask> claimNextExecution(LocalDateTime now) {
        return jdbc.sql("""
                        WITH candidate AS (
                            SELECT sync_job_id
                            FROM collection_executions
                            WHERE status = 'QUEUED' AND next_attempt_at <= :now
                            ORDER BY next_attempt_at, created_at, sync_job_id
                            FOR UPDATE SKIP LOCKED
                            LIMIT 1
                        ), claimed AS (
                            UPDATE collection_executions e
                            SET status = 'COLLECTING', updated_at = :now
                            FROM candidate
                            WHERE e.sync_job_id = candidate.sync_job_id
                            RETURNING e.*
                        )
                        SELECT claimed.*,
                               (
                                   SELECT payload ->> 'traceId'
                                   FROM finance_event_outbox
                                   WHERE sync_job_id = claimed.sync_job_id
                                     AND event_type = 'SyncAccepted'
                                   ORDER BY created_at, message_id
                                   LIMIT 1
                               ) AS trace_id
                        FROM claimed
                        """)
                .param("now", now)
                .query((rs, rowNum) -> new FinanceCollectionTask(
                        rs.getObject("sync_job_id", UUID.class),
                        rs.getObject("command_message_id", UUID.class),
                        rs.getObject("account_ref", UUID.class),
                        rs.getLong("sync_version"),
                        rs.getObject("connection_ref", UUID.class),
                        rs.getLong("connection_version"),
                        rs.getObject("from_date", java.time.LocalDate.class),
                        rs.getObject("to_date", java.time.LocalDate.class),
                        rs.getObject("deadline_at", LocalDateTime.class),
                        rs.getString("trace_id")))
                .optional();
    }

    @Override
    public Optional<EncryptedBankCredential> findActive(
            UUID connectionRef,
            UUID accountRef,
            long connectionVersion
    ) {
        return jdbc.sql("""
                        SELECT c.connected_id_ciphertext, c.organization_code,
                               a.provider_account_ref_ciphertext
                        FROM financial_connections c
                        JOIN connected_accounts a ON a.connection_ref = c.connection_ref
                        WHERE c.connection_ref = :connectionRef
                          AND a.account_ref = :accountRef
                          AND c.connection_version = :connectionVersion
                          AND a.connection_version = :connectionVersion
                          AND c.active AND a.active
                        """)
                .param("connectionRef", connectionRef)
                .param("accountRef", accountRef)
                .param("connectionVersion", connectionVersion)
                .query((rs, rowNum) -> new EncryptedBankCredential(
                        rs.getString("connected_id_ciphertext"),
                        rs.getString("organization_code"),
                        rs.getString("provider_account_ref_ciphertext")))
                .optional();
    }

    @Override
    @Transactional
    public void complete(
            FinanceCollectionTask task,
            List<CollectedBankTransaction> transactions,
            LocalDateTime completedAt
    ) {
        lockCurrentConnection(task);
        List<OutboxEvent> events = chunkEvents(task, transactions, completedAt);
        LocalDateTime completionTime = completedAt.plusNanos(events.size() * 1_000L);
        events.add(completedEvent(task, events.size(), transactions.size(), completionTime));
        events.forEach(this::insertOutbox);

        int chunks = events.size() - 1;
        int updated = jdbc.sql("""
                        UPDATE collection_executions
                        SET status = 'SUCCEEDED', next_attempt_at = NULL,
                            next_chunk_index = :chunks,
                            collected_transaction_count = :items,
                            total_chunk_count = :chunks,
                            total_transaction_count = :items,
                            lease_owner = NULL, lease_until = NULL,
                            completed_at = :completedAt, updated_at = :completedAt
                        WHERE sync_job_id = :syncJobId AND status = 'COLLECTING'
                        """)
                .param("chunks", chunks)
                .param("items", transactions.size())
                .param("completedAt", completionTime)
                .param("syncJobId", task.syncJobId())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Collection execution is no longer active");
        }
    }

    @Override
    @Transactional
    public void fail(
            FinanceCollectionTask task,
            FinanceCollectionFailureCode failureCode,
            boolean retryable,
            String safeMessage,
            LocalDateTime failedAt
    ) {
        insertOutbox(failedEvent(task, failureCode, retryable, safeMessage, failedAt));
        String status = switch (failureCode) {
            case REAUTH_REQUIRED -> "REAUTH_REQUIRED";
            case DEADLINE_EXCEEDED -> "TIMED_OUT";
            default -> "FAILED";
        };
        int updated = jdbc.sql("""
                        UPDATE collection_executions
                        SET status = :status, next_attempt_at = NULL,
                            lease_owner = NULL, lease_until = NULL,
                            failure_code = :failureCode, safe_message = :safeMessage,
                            completed_at = :failedAt, updated_at = :failedAt
                        WHERE sync_job_id = :syncJobId AND status = 'COLLECTING'
                        """)
                .param("status", status)
                .param("failureCode", failureCode.name())
                .param("safeMessage", safeMessage)
                .param("failedAt", failedAt)
                .param("syncJobId", task.syncJobId())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Collection execution is no longer active");
        }
    }

    @Override
    @Transactional
    public Optional<PendingFinanceEvent> claimNextEvent(LocalDateTime now) {
        return jdbc.sql("""
                        WITH candidate AS (
                            SELECT message_id
                            FROM finance_event_outbox
                            WHERE status = 'PENDING' AND next_attempt_at <= :now
                            ORDER BY next_attempt_at, created_at, message_id
                            FOR UPDATE SKIP LOCKED
                            LIMIT 1
                        )
                        UPDATE finance_event_outbox o
                        SET status = 'PROCESSING', attempt_count = attempt_count + 1,
                            next_attempt_at = NULL, processing_started_at = :now,
                            updated_at = :now
                        FROM candidate
                        WHERE o.message_id = candidate.message_id
                        RETURNING o.message_id, o.account_ref, o.topic, o.payload::text AS payload
                        """)
                .param("now", now)
                .query((rs, rowNum) -> new PendingFinanceEvent(
                        rs.getObject("message_id", UUID.class),
                        rs.getObject("account_ref", UUID.class),
                        rs.getString("topic"),
                        compactJson(rs.getString("payload"))))
                .optional();
    }

    @Override
    @Transactional
    public void markPublished(UUID messageId, LocalDateTime publishedAt) {
        int updated = jdbc.sql("""
                        UPDATE finance_event_outbox
                        SET status = 'PUBLISHED', processing_started_at = NULL,
                            completed_at = :publishedAt, updated_at = :publishedAt
                        WHERE message_id = :messageId AND status = 'PROCESSING'
                        """)
                .param("publishedAt", publishedAt)
                .param("messageId", messageId)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Finance event is no longer processing");
        }
    }

    private List<OutboxEvent> chunkEvents(
            FinanceCollectionTask task,
            List<CollectedBankTransaction> transactions,
            LocalDateTime startedAt
    ) {
        List<OutboxEvent> events = new ArrayList<>();
        int offset = 0;
        int chunkIndex = 0;
        while (offset < transactions.size()) {
            int end = Math.min(offset + MAX_CHUNK_ITEMS, transactions.size());
            UUID messageId = UUID.randomUUID();
            LocalDateTime eventAt = startedAt.plusNanos(chunkIndex * 1_000L);
            String payload = chunkPayload(task, messageId, chunkIndex, transactions.subList(offset, end), eventAt);
            while (payload.getBytes(StandardCharsets.UTF_8).length > MAX_CHUNK_BYTES && end > offset + 1) {
                end--;
                payload = chunkPayload(task, messageId, chunkIndex, transactions.subList(offset, end), eventAt);
            }
            if (payload.getBytes(StandardCharsets.UTF_8).length > MAX_CHUNK_BYTES) {
                throw new FinanceCollectionException(FinanceCollectionFailureCode.INVALID_REQUEST, false,
                        "단일 거래 메시지가 허용 크기를 초과했습니다.");
            }
            events.add(new OutboxEvent(messageId, task, "TransactionsChunkCollected", payload, eventAt));
            offset = end;
            chunkIndex++;
        }
        return events;
    }

    private void lockCurrentConnection(FinanceCollectionTask task) {
        boolean current = jdbc.sql("""
                        SELECT 1
                        FROM financial_connections c
                        JOIN connected_accounts a ON a.connection_ref = c.connection_ref
                        WHERE c.connection_ref = :connectionRef
                          AND a.account_ref = :accountRef
                          AND c.connection_version = :connectionVersion
                          AND a.connection_version = :connectionVersion
                          AND c.active AND a.active
                        FOR SHARE OF c, a
                        """)
                .param("connectionRef", task.connectionRef())
                .param("accountRef", task.accountRef())
                .param("connectionVersion", task.connectionVersion())
                .query(Integer.class)
                .optional()
                .isPresent();
        if (!current) {
            throw new FinanceCollectionException(FinanceCollectionFailureCode.CONNECTION_REVOKED, false,
                    "금융 연결이 해제되었거나 변경되었습니다.");
        }
    }

    private String compactJson(String payload) {
        return objectMapper.writeValueAsString(objectMapper.readTree(payload));
    }

    private String chunkPayload(
            FinanceCollectionTask task,
            UUID messageId,
            int chunkIndex,
            List<CollectedBankTransaction> transactions,
            LocalDateTime eventAt
    ) {
        ObjectNode root = envelope(task, messageId, "TransactionsChunkCollected", eventAt);
        ObjectNode data = root.putObject("data");
        data.put("chunkIndex", chunkIndex);
        data.put("collectedAt", timestamp(eventAt));
        ArrayNode items = data.putArray("items");
        for (CollectedBankTransaction transaction : transactions) {
            ObjectNode item = items.addObject();
            item.put("transactionKey", sha256(task.accountRef() + transaction.providerTransactionId()));
            item.put("providerTransactionId", transaction.providerTransactionId());
            item.put("bookedAt", timestamp(transaction.bookedAt()));
            item.put("direction", transaction.direction().name());
            item.put("amount", transaction.amount().toBigIntegerExact().toString());
            item.put("currency", transaction.currency());
            if (transaction.balanceAfter() == null) {
                item.putNull("balanceAfter");
            } else {
                item.put("balanceAfter", transaction.balanceAfter().toBigIntegerExact().toString());
            }
            if (transaction.description() == null) {
                item.putNull("description");
            } else {
                item.put("description", transaction.description());
            }
        }
        return objectMapper.writeValueAsString(root);
    }

    private OutboxEvent completedEvent(
            FinanceCollectionTask task,
            int totalChunks,
            int totalItems,
            LocalDateTime eventAt
    ) {
        UUID messageId = UUID.randomUUID();
        ObjectNode root = envelope(task, messageId, "TransactionsCollectionCompleted", eventAt);
        ObjectNode data = root.putObject("data");
        data.put("fromDate", task.fromDate().toString());
        data.put("toDate", task.toDate().toString());
        data.put("totalChunks", totalChunks);
        data.put("totalItems", totalItems);
        data.put("collectionCompletedAt", timestamp(eventAt));
        return new OutboxEvent(messageId, task, "TransactionsCollectionCompleted",
                objectMapper.writeValueAsString(root), eventAt);
    }

    private OutboxEvent failedEvent(
            FinanceCollectionTask task,
            FinanceCollectionFailureCode failureCode,
            boolean retryable,
            String safeMessage,
            LocalDateTime eventAt
    ) {
        UUID messageId = UUID.randomUUID();
        ObjectNode root = envelope(task, messageId, "TransactionsSyncFailed", eventAt);
        ObjectNode data = root.putObject("data");
        data.put("failureCode", failureCode.name());
        data.put("retryable", retryable);
        data.put("safeMessage", safeMessage);
        data.put("failedAt", timestamp(eventAt));
        return new OutboxEvent(messageId, task, "TransactionsSyncFailed",
                objectMapper.writeValueAsString(root), eventAt);
    }

    private ObjectNode envelope(
            FinanceCollectionTask task,
            UUID messageId,
            String messageType,
            LocalDateTime eventAt
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("messageId", messageId.toString());
        root.put("messageType", messageType);
        root.put("schemaVersion", 1);
        root.put("occurredAt", timestamp(eventAt));
        root.put("syncJobId", task.syncJobId().toString());
        root.put("accountRef", task.accountRef().toString());
        root.put("syncVersion", task.syncVersion());
        root.put("connectionVersion", task.connectionVersion());
        root.put("causationId", task.commandMessageId().toString());
        if (task.traceId() == null) {
            root.putNull("traceId");
        } else {
            root.put("traceId", task.traceId());
        }
        return root;
    }

    private void insertOutbox(OutboxEvent event) {
        jdbc.sql("""
                        INSERT INTO finance_event_outbox (
                            message_id, sync_job_id, account_ref, event_type, topic,
                            payload_hash, payload, status, attempt_count, next_attempt_at,
                            created_at, updated_at
                        ) VALUES (
                            :messageId, :syncJobId, :accountRef, :eventType, :topic,
                            :payloadHash, CAST(:payload AS jsonb), 'PENDING', 0, :eventAt,
                            :eventAt, :eventAt
                        )
                        """)
                .param("messageId", event.messageId())
                .param("syncJobId", event.task().syncJobId())
                .param("accountRef", event.task().accountRef())
                .param("eventType", event.eventType())
                .param("topic", EVENT_TOPIC)
                .param("payloadHash", sha256(event.payload()))
                .param("payload", event.payload())
                .param("eventAt", event.eventAt())
                .update();
    }

    private String timestamp(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC).toString();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record OutboxEvent(
            UUID messageId,
            FinanceCollectionTask task,
            String eventType,
            String payload,
            LocalDateTime eventAt
    ) {
    }
}
