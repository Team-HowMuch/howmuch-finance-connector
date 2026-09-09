package com.amateur.howmuchfinanceconnector.adapter.out.persistence.sync;

import com.amateur.howmuchfinanceconnector.application.port.out.sync.FinanceCommandIntakeStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FinanceCommandIntakeJdbcAdapter implements FinanceCommandIntakeStore {

    private static final String EVENT_TOPIC = "finance.sync.event.v1";

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public FinanceCommandIntakeJdbcAdapter(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void lock(UUID messageId, UUID syncJobId) {
        lock(messageId);
        lock(syncJobId);
    }

    private void lock(UUID reference) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(CAST(:reference AS text), 0))")
                .param("reference", reference)
                .query(Long.class)
                .single();
    }

    @Override
    public Optional<InboxSnapshot> findInbox(UUID messageId) {
        return jdbc.sql("SELECT payload_hash FROM finance_command_inbox WHERE message_id = :messageId")
                .param("messageId", messageId)
                .query((rs, rowNum) -> new InboxSnapshot(rs.getString("payload_hash")))
                .optional();
    }

    @Override
    public void insertInbox(ReceivedCommand command) {
        jdbc.sql("""
                        INSERT INTO finance_command_inbox (
                            message_id, sync_job_id, account_ref, message_type,
                            schema_version, payload_hash, received_at, processed_at
                        ) VALUES (
                            :messageId, :syncJobId, :accountRef, 'SyncAccountTransactions',
                            1, :payloadHash, :receivedAt, NULL
                        )
                        """)
                .param("messageId", command.messageId())
                .param("syncJobId", command.syncJobId())
                .param("accountRef", command.accountRef())
                .param("payloadHash", command.payloadHash())
                .param("receivedAt", command.receivedAt())
                .update();
    }

    @Override
    public Optional<ExecutionSnapshot> findExecution(UUID syncJobId) {
        return jdbc.sql("SELECT command_payload_hash FROM collection_executions WHERE sync_job_id = :syncJobId")
                .param("syncJobId", syncJobId)
                .query((rs, rowNum) -> new ExecutionSnapshot(rs.getString("command_payload_hash")))
                .optional();
    }

    @Override
    public Optional<ActiveAccountSnapshot> findActiveAccount(UUID accountRef) {
        return jdbc.sql("""
                        SELECT a.connection_ref, a.connection_version
                        FROM connected_accounts a
                        JOIN financial_connections c ON c.connection_ref = a.connection_ref
                        WHERE a.account_ref = :accountRef AND a.active AND c.active
                        """)
                .param("accountRef", accountRef)
                .query((rs, rowNum) -> new ActiveAccountSnapshot(
                        rs.getObject("connection_ref", UUID.class),
                        rs.getLong("connection_version")))
                .optional();
    }

    @Override
    public void markProcessed(UUID messageId, java.time.LocalDateTime processedAt) {
        jdbc.sql("""
                        UPDATE finance_command_inbox
                        SET processed_at = :processedAt
                        WHERE message_id = :messageId
                        """)
                .param("processedAt", processedAt)
                .param("messageId", messageId)
                .update();
    }

    @Override
    public void createAcceptedExecution(AcceptedExecution execution) {
        jdbc.sql("""
                        INSERT INTO collection_executions (
                            sync_job_id, command_message_id, account_ref, connection_ref,
                            sync_version, connection_version, command_payload_hash,
                            from_date, to_date, deadline_at, status, next_attempt_at,
                            created_at, updated_at
                        ) VALUES (
                            :syncJobId, :messageId, :accountRef, :connectionRef,
                            :syncVersion, :connectionVersion, :commandHash,
                            :fromDate, :toDate, :deadlineAt, 'QUEUED', :acceptedAt,
                            :acceptedAt, :acceptedAt
                        )
                        """)
                .param("syncJobId", execution.syncJobId())
                .param("messageId", execution.messageId())
                .param("accountRef", execution.accountRef())
                .param("connectionRef", execution.connectionRef())
                .param("syncVersion", execution.syncVersion())
                .param("connectionVersion", execution.connectionVersion())
                .param("commandHash", execution.commandHash())
                .param("fromDate", execution.fromDate())
                .param("toDate", execution.toDate())
                .param("deadlineAt", execution.deadlineAt())
                .param("acceptedAt", execution.acceptedAt())
                .update();

        String payload = acceptedPayload(execution);
        jdbc.sql("""
                        INSERT INTO finance_event_outbox (
                            message_id, sync_job_id, account_ref, event_type, topic,
                            payload_hash, payload, status, attempt_count, next_attempt_at,
                            created_at, updated_at
                        ) VALUES (
                            :eventMessageId, :syncJobId, :accountRef, 'SyncAccepted', :topic,
                            :payloadHash, CAST(:payload AS jsonb), 'PENDING', 0, :acceptedAt,
                            :acceptedAt, :acceptedAt
                        )
                        """)
                .param("eventMessageId", execution.eventMessageId())
                .param("syncJobId", execution.syncJobId())
                .param("accountRef", execution.accountRef())
                .param("topic", EVENT_TOPIC)
                .param("payloadHash", sha256(payload))
                .param("payload", payload)
                .param("acceptedAt", execution.acceptedAt())
                .update();
        markProcessed(execution.messageId(), execution.acceptedAt());
    }

    private String acceptedPayload(AcceptedExecution execution) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("messageId", execution.eventMessageId().toString());
        root.put("messageType", "SyncAccepted");
        root.put("schemaVersion", 1);
        String acceptedAt = execution.acceptedAt().toInstant(ZoneOffset.UTC).toString();
        root.put("occurredAt", acceptedAt);
        root.put("syncJobId", execution.syncJobId().toString());
        root.put("accountRef", execution.accountRef().toString());
        root.put("syncVersion", execution.syncVersion());
        root.put("connectionVersion", execution.connectionVersion());
        root.put("causationId", execution.messageId().toString());
        if (execution.traceId() == null) {
            root.putNull("traceId");
        } else {
            root.put("traceId", execution.traceId());
        }
        root.putObject("data").put("acceptedAt", acceptedAt);
        return objectMapper.writeValueAsString(root);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
