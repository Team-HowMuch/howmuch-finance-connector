package com.amateur.howmuchfinanceconnector.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "financial_connections")
public class FinancialConnectionJpaEntity {

    @Id
    @Column(name = "connection_ref", nullable = false)
    private UUID connectionRef;

    @Column(name = "connection_version", nullable = false)
    private int connectionVersion;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "institution_type", nullable = false, length = 30)
    private String institutionType;

    @Column(name = "organization_code", nullable = false, length = 50)
    private String organizationCode;

    @Column(name = "connected_id", nullable = false, length = 255)
    private String connectedId;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FinancialConnectionJpaEntity() {
    }

    public FinancialConnectionJpaEntity(
            UUID connectionRef,
            int connectionVersion,
            String provider,
            String institutionType,
            String organizationCode,
            String connectedId,
            String requestHash,
            Instant now
    ) {
        this.connectionRef = connectionRef;
        update(connectionVersion, provider, institutionType, organizationCode, connectedId, requestHash, now);
        this.createdAt = now;
    }

    public void update(
            int connectionVersion,
            String provider,
            String institutionType,
            String organizationCode,
            String connectedId,
            String requestHash,
            Instant now
    ) {
        this.connectionVersion = connectionVersion;
        this.provider = provider;
        this.institutionType = institutionType;
        this.organizationCode = organizationCode;
        this.connectedId = connectedId;
        this.requestHash = requestHash;
        this.active = true;
        this.updatedAt = now;
    }

    public void revoke(Instant now) {
        this.active = false;
        this.updatedAt = now;
    }

    public UUID getConnectionRef() { return connectionRef; }
    public int getConnectionVersion() { return connectionVersion; }
    public String getRequestHash() { return requestHash; }
    public boolean isActive() { return active; }
    public String getConnectedId() { return connectedId; }
    public String getOrganizationCode() { return organizationCode; }
}
