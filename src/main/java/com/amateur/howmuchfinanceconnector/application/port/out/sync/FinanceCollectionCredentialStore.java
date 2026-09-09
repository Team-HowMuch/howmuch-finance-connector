package com.amateur.howmuchfinanceconnector.application.port.out.sync;

import java.util.Optional;
import java.util.UUID;

public interface FinanceCollectionCredentialStore {
    Optional<EncryptedBankCredential> findActive(
            UUID connectionRef,
            UUID accountRef,
            long connectionVersion
    );

    record EncryptedBankCredential(
            String connectedIdCiphertext,
            String organizationCode,
            String providerAccountRefCiphertext
    ) {
    }
}
