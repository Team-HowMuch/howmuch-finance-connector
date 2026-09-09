package com.amateur.howmuchfinanceconnector.adapter.out.persistence;

import com.amateur.howmuchfinanceconnector.application.FinanceSyncCommand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "collection_executions")
public class CollectionExecutionJpaEntity {

    @Id
    @Column(name = "sync_job_id", nullable = false)
    private UUID syncJobId;

    @Column(name = "command_message_id", nullable = false)
    private UUID commandMessageId;

    @Column(name = "account_ref", nullable = false)
    private UUID accountRef;

    @Column(name = "connection_ref", nullable = false)
    private UUID connectionRef;

    @Column(name = "sync_version", nullable = false)
    private long syncVersion;

    @Column(name = "connection_version", nullable = false)
    private long connectionVersion;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "next_chunk_index", nullable = false)
    private int nextChunkIndex;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "page_attempt_count", nullable = false)
    private int pageAttemptCount;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "lease_version", nullable = false)
    private long leaseVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CollectionExecutionJpaEntity() {
    }

    public CollectionExecutionJpaEntity(FinanceSyncCommand command, String requestHash, Instant now) {
        this.syncJobId = command.syncJobId();
        this.commandMessageId = command.messageId();
        this.accountRef = command.accountRef();
        this.connectionRef = command.connectionRef();
        this.syncVersion = command.syncVersion();
        this.connectionVersion = command.connectionVersion();
        this.fromDate = command.fromDate();
        this.toDate = command.toDate();
        this.deadlineAt = command.deadlineAt();
        this.status = "ACCEPTED";
        this.requestHash = requestHash;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getSyncJobId() { return syncJobId; }
    public String getRequestHash() { return requestHash; }
    public UUID getCommandMessageId() { return commandMessageId; }
    public UUID getAccountRef() { return accountRef; }
    public UUID getConnectionRef() { return connectionRef; }
    public long getSyncVersion() { return syncVersion; }
    public long getConnectionVersion() { return connectionVersion; }
    public LocalDate getFromDate() { return fromDate; }
    public LocalDate getToDate() { return toDate; }
    public String getStatus() { return status; }

    public void collected(Instant now) {
        this.status = "COLLECTED";
        this.updatedAt = now;
    }

    public void failed(Instant now) {
        this.status = "FAILED";
        this.updatedAt = now;
    }
}
