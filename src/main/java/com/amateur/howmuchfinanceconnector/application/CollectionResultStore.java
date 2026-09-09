package com.amateur.howmuchfinanceconnector.application;

import com.amateur.howmuchfinanceconnector.adapter.out.persistence.CollectionExecutionJpaEntity;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.CollectionExecutionJpaRepository;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.OutboxEventJpaEntity;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.OutboxEventJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CollectionResultStore {

    private final CollectionExecutionJpaRepository executions;
    private final OutboxEventJpaRepository outbox;
    private final ObjectMapper objectMapper;
    private final String eventTopic;

    public CollectionResultStore(
            CollectionExecutionJpaRepository executions,
            OutboxEventJpaRepository outbox,
            ObjectMapper objectMapper,
            @Value("${finance.kafka.event-topic:finance.sync.event.v1}") String eventTopic
    ) {
        this.executions = executions;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.eventTopic = eventTopic;
    }

    @Transactional
    public void completeEmpty(CollectionLease lease) {
        UUID syncJobId = lease.syncJobId();
        CollectionExecutionJpaEntity execution = required(syncJobId);
        Instant now = Instant.now();
        execution.collected(lease.version(), now);
        ObjectNode data = objectMapper.createObjectNode();
        data.put("fromDate", execution.getFromDate().toString());
        data.put("toDate", execution.getToDate().toString());
        data.put("totalChunks", 0);
        data.put("totalItems", 0);
        data.put("collectionCompletedAt", now.toString());
        outbox.save(event(execution, "TransactionsCollectionCompleted", data, now));
    }

    @Transactional
    public void fail(CollectionLease lease, String errorCode, String category, String suggestedAction, String safeMessage) {
        UUID syncJobId = lease.syncJobId();
        CollectionExecutionJpaEntity execution = required(syncJobId);
        Instant now = Instant.now();
        execution.failed(lease.version(), now);
        ObjectNode data = objectMapper.createObjectNode();
        data.put("errorCode", errorCode);
        data.put("category", category);
        data.put("automaticRetry", false);
        data.put("suggestedAction", suggestedAction);
        data.put("safeMessage", safeMessage);
        data.put("failedAt", now.toString());
        outbox.save(event(execution, "TransactionsSyncFailed", data, now));
    }

    @Transactional
    public void retryOrFail(CollectionLease lease, String errorCode) {
        CollectionExecutionJpaEntity execution = required(lease.syncJobId());
        Instant now = Instant.now();
        if (execution.getPageAttemptCount() >= 3 || !now.isBefore(execution.getDeadlineAt())) {
            fail(execution, lease.version(), errorCode, now);
            return;
        }
        long baseDelaySeconds = execution.getPageAttemptCount() == 1 ? 2 : 8;
        long jitterMillis = Math.floorMod(lease.syncJobId().hashCode(), 501);
        execution.retry(lease.version(), now.plusSeconds(baseDelaySeconds).plusMillis(jitterMillis), now);
    }

    private CollectionExecutionJpaEntity required(UUID syncJobId) {
        return executions.findById(syncJobId).orElseThrow(() -> new IllegalStateException("collection execution not found"));
    }

    private void fail(CollectionExecutionJpaEntity execution, long leaseVersion, String errorCode, Instant now) {
        execution.failed(leaseVersion, now);
        ObjectNode data = objectMapper.createObjectNode();
        data.put("errorCode", errorCode);
        data.put("category", "TEMPORARY");
        data.put("automaticRetry", false);
        data.put("suggestedAction", "RETRY");
        data.put("safeMessage", "금융기관 조회에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        data.put("failedAt", now.toString());
        outbox.save(event(execution, "TransactionsSyncFailed", data, now));
    }

    private OutboxEventJpaEntity event(
            CollectionExecutionJpaEntity execution,
            String type,
            ObjectNode data,
            Instant now
    ) {
        UUID messageId = UUID.randomUUID();
        ObjectNode root = objectMapper.createObjectNode();
        root.put("messageId", messageId.toString());
        root.put("messageType", type);
        root.put("schemaVersion", 1);
        root.put("occurredAt", now.toString());
        root.put("syncJobId", execution.getSyncJobId().toString());
        root.put("accountRef", execution.getAccountRef().toString());
        root.put("syncVersion", execution.getSyncVersion());
        root.put("connectionVersion", execution.getConnectionVersion());
        root.put("causationId", execution.getCommandMessageId().toString());
        root.putNull("traceId");
        root.set("data", data);
        try {
            return new OutboxEventJpaEntity(
                    messageId,
                    execution.getSyncJobId(),
                    eventTopic,
                    execution.getAccountRef().toString(),
                    objectMapper.writeValueAsString(root),
                    now
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("finance event cannot be serialized", exception);
        }
    }
}
