package com.amateur.howmuchfinanceconnector.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_messages", uniqueConstraints = @UniqueConstraint(
        name = "processed_messages_uq_consumer_message",
        columnNames = {"consumer_name", "message_id"}
))
public class ProcessedMessageJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consumer_name", nullable = false, length = 100)
    private String consumerName;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProcessedMessageJpaEntity() {
    }

    public ProcessedMessageJpaEntity(String consumerName, UUID messageId, String payloadHash, Instant createdAt) {
        this.consumerName = consumerName;
        this.messageId = messageId;
        this.payloadHash = payloadHash;
        this.createdAt = createdAt;
    }

    public String getPayloadHash() { return payloadHash; }
}
