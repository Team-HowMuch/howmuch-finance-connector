package com.amateur.howmuchfinanceconnector.application.connection.service;

import com.amateur.howmuchfinanceconnector.application.port.in.connection.RegisterFinanceConnectionCommand;
import com.amateur.howmuchfinanceconnector.application.port.out.connection.FinanceConnectionStore;
import com.amateur.howmuchfinanceconnector.domain.connection.FinanceAccountType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FinanceConnectionServiceTest {

    private static final UUID CONNECTION_REF = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ACCOUNT_REF = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Test
    void keepsVersionForIdenticalRegistrationAndIncrementsItWhenAccountsChange() {
        var store = new InMemoryStore();
        var service = new FinanceConnectionService(
                store,
                new com.amateur.howmuchfinanceconnector.application.port.out.connection.ConnectionSecretCipher() {
                    @Override
                    public String encrypt(String plaintext) {
                        return "encrypted:" + plaintext;
                    }

                    @Override
                    public String decrypt(String ciphertext) {
                        return ciphertext.substring("encrypted:".length());
                    }
                },
                Clock.fixed(Instant.parse("2026-09-09T00:00:00Z"), ZoneOffset.UTC));

        var first = service.register(command("11"));
        var duplicate = service.register(command("11"));
        var changed = service.register(command("12"));

        assertThat(first.connectionVersion()).isEqualTo(1);
        assertThat(first.changed()).isTrue();
        assertThat(duplicate.connectionVersion()).isEqualTo(1);
        assertThat(duplicate.changed()).isFalse();
        assertThat(changed.connectionVersion()).isEqualTo(2);
        assertThat(store.saved.connectedIdCiphertext()).isEqualTo("encrypted:connected-id");
    }

    private RegisterFinanceConnectionCommand command(String productCode) {
        return new RegisterFinanceConnectionCommand(
                CONNECTION_REF,
                "connected-id",
                "0088",
                List.of(new RegisterFinanceConnectionCommand.Account(
                        ACCOUNT_REF,
                        "provider-account",
                        FinanceAccountType.DEPOSIT_TRUST,
                        productCode,
                        "KRW")));
    }

    private static final class InMemoryStore implements FinanceConnectionStore {
        private final Map<UUID, UUID> owners = new HashMap<>();
        private StoredConnection saved;

        @Override
        public void lock(UUID connectionRef) {
        }

        @Override
        public Optional<ConnectionSnapshot> find(UUID connectionRef) {
            return saved == null
                    ? Optional.empty()
                    : Optional.of(new ConnectionSnapshot(saved.version(), saved.registrationHash(), true));
        }

        @Override
        public Optional<UUID> findOwningConnection(UUID accountRef) {
            return Optional.ofNullable(owners.get(accountRef));
        }

        @Override
        public void save(StoredConnection connection) {
            saved = connection;
            connection.accounts().forEach(account -> owners.put(account.accountRef(), connection.connectionRef()));
        }

        @Override
        public void revoke(UUID connectionRef, java.time.LocalDateTime now) {
        }
    }
}
