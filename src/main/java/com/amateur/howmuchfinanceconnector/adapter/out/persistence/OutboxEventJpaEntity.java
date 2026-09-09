package com.amateur.howmuchfinanceconnector.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEventJpaEntity {

    @Id
    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "sync_job_id", nullable = false)
    private UUID syncJobId;

    @Column(nullable = false, length = 200)
    private String topic;

    @Column(name = "message_key", nullable = false, length = 100)
    private String messageKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEventJpaEntity() {
    }

    public OutboxEventJpaEntity(UUID messageId, UUID syncJobId, String topic, String messageKey, String payload, Instant now) {
        this.messageId = messageId;
        this.syncJobId = syncJobId;
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
        this.status = "PENDING";
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    public UUID getMessageId() { return messageId; }
    public String getTopic() { return topic; }
    public String getMessageKey() { return messageKey; }
    public String getPayload() { return payload; }
    public void published(Instant now) { this.status = "PUBLISHED"; this.publishedAt = now; }
}
