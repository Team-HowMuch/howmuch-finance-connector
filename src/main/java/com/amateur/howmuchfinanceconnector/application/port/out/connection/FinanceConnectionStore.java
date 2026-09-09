package com.amateur.howmuchfinanceconnector.application.port.out.connection;

import com.amateur.howmuchfinanceconnector.domain.connection.FinanceAccountType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinanceConnectionStore {

    void lock(UUID connectionRef);

    Optional<ConnectionSnapshot> find(UUID connectionRef);

    Optional<UUID> findOwningConnection(UUID accountRef);

    void save(StoredConnection connection);

    void revoke(UUID connectionRef, LocalDateTime now);

    record ConnectionSnapshot(long version, String registrationHash, boolean active) {
    }

    record StoredConnection(
            UUID connectionRef,
            long version,
            String organizationCode,
            String connectedIdCiphertext,
            String registrationHash,
            List<StoredAccount> accounts,
            LocalDateTime now
    ) {
    }

    record StoredAccount(
            UUID accountRef,
            String providerAccountRefCiphertext,
            FinanceAccountType accountType,
            String productCode,
            String currency
    ) {
    }
}
