package com.amateur.howmuchfinanceconnector.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "connected_accounts")
public class ConnectedAccountJpaEntity {

    @Id
    @Column(name = "account_ref", nullable = false)
    private UUID accountRef;

    @Column(name = "connection_ref", nullable = false)
    private UUID connectionRef;

    @Column(name = "provider_account_ref", nullable = false, length = 150)
    private String providerAccountRef;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConnectedAccountJpaEntity() {
    }

    public ConnectedAccountJpaEntity(UUID accountRef, UUID connectionRef, String providerAccountRef, Instant now) {
        this.accountRef = accountRef;
        this.createdAt = now;
        update(connectionRef, providerAccountRef, now);
    }

    public void update(UUID connectionRef, String providerAccountRef, Instant now) {
        this.connectionRef = connectionRef;
        this.providerAccountRef = providerAccountRef;
        this.active = true;
        this.updatedAt = now;
    }

    public void revoke(Instant now) {
        this.active = false;
        this.updatedAt = now;
    }

    public UUID getAccountRef() { return accountRef; }
    public UUID getConnectionRef() { return connectionRef; }
    public String getProviderAccountRef() { return providerAccountRef; }
    public boolean isActive() { return active; }
}
