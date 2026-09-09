package com.amateur.howmuchfinanceconnector.adapter.out.persistence.connection;

import com.amateur.howmuchfinanceconnector.application.connection.FinanceConnectionAccountConflictException;
import com.amateur.howmuchfinanceconnector.application.port.out.connection.FinanceConnectionStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FinanceConnectionJdbcAdapter implements FinanceConnectionStore {

    private final JdbcClient jdbc;

    public FinanceConnectionJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void lock(UUID connectionRef) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(CAST(:connectionRef AS text), 0))")
                .param("connectionRef", connectionRef)
                .query(Long.class)
                .single();
    }

    @Override
    public Optional<ConnectionSnapshot> find(UUID connectionRef) {
        return jdbc.sql("""
                        SELECT connection_version, registration_hash, active
                        FROM financial_connections
                        WHERE connection_ref = :connectionRef
                        """)
                .param("connectionRef", connectionRef)
                .query((rs, rowNum) -> new ConnectionSnapshot(
                        rs.getLong("connection_version"),
                        rs.getString("registration_hash"),
                        rs.getBoolean("active")))
                .optional();
    }

    @Override
    public Optional<UUID> findOwningConnection(UUID accountRef) {
        return jdbc.sql("SELECT connection_ref FROM connected_accounts WHERE account_ref = :accountRef")
                .param("accountRef", accountRef)
                .query(UUID.class)
                .optional();
    }

    @Override
    public void save(StoredConnection connection) {
        jdbc.sql("""
                        INSERT INTO financial_connections (
                            connection_ref, connection_version, provider, institution_type,
                            organization_code, connected_id_ciphertext, registration_hash,
                            active, created_at, updated_at
                        ) VALUES (
                            :connectionRef, :version, 'CODEF', 'BANK',
                            :organizationCode, :connectedIdCiphertext, :registrationHash,
                            true, :now, :now
                        )
                        ON CONFLICT (connection_ref) DO UPDATE SET
                            connection_version = EXCLUDED.connection_version,
                            organization_code = EXCLUDED.organization_code,
                            connected_id_ciphertext = EXCLUDED.connected_id_ciphertext,
                            registration_hash = EXCLUDED.registration_hash,
                            active = true,
                            updated_at = EXCLUDED.updated_at
                        """)
                .param("connectionRef", connection.connectionRef())
                .param("version", connection.version())
                .param("organizationCode", connection.organizationCode())
                .param("connectedIdCiphertext", connection.connectedIdCiphertext())
                .param("registrationHash", connection.registrationHash())
                .param("now", connection.now())
                .update();

        jdbc.sql("""
                        UPDATE connected_accounts
                        SET active = false, updated_at = :now
                        WHERE connection_ref = :connectionRef AND active
                        """)
                .param("connectionRef", connection.connectionRef())
                .param("now", connection.now())
                .update();

        for (StoredAccount account : connection.accounts()) {
            int updated = jdbc.sql("""
                            INSERT INTO connected_accounts (
                                account_ref, connection_ref, connection_version,
                                provider_account_ref_ciphertext, account_type, product_code,
                                currency, active, created_at, updated_at
                            ) VALUES (
                                :accountRef, :connectionRef, :version,
                                :providerAccountRefCiphertext, :accountType, :productCode,
                                :currency, true, :now, :now
                            )
                            ON CONFLICT (account_ref) DO UPDATE SET
                                connection_version = EXCLUDED.connection_version,
                                provider_account_ref_ciphertext = EXCLUDED.provider_account_ref_ciphertext,
                                account_type = EXCLUDED.account_type,
                                product_code = EXCLUDED.product_code,
                                currency = EXCLUDED.currency,
                                active = true,
                                updated_at = EXCLUDED.updated_at
                            WHERE connected_accounts.connection_ref = EXCLUDED.connection_ref
                            """)
                    .param("accountRef", account.accountRef())
                    .param("connectionRef", connection.connectionRef())
                    .param("version", connection.version())
                    .param("providerAccountRefCiphertext", account.providerAccountRefCiphertext())
                    .param("accountType", account.accountType().name())
                    .param("productCode", account.productCode())
                    .param("currency", account.currency())
                    .param("now", connection.now())
                    .update();
            if (updated != 1) {
                throw new FinanceConnectionAccountConflictException();
            }
        }
    }

    @Override
    public void revoke(UUID connectionRef, LocalDateTime now) {
        jdbc.sql("""
                        UPDATE connected_accounts
                        SET active = false, updated_at = :now
                        WHERE connection_ref = :connectionRef AND active
                        """)
                .param("connectionRef", connectionRef)
                .param("now", now)
                .update();
        jdbc.sql("""
                        UPDATE financial_connections
                        SET active = false, updated_at = :now
                        WHERE connection_ref = :connectionRef AND active
                        """)
                .param("connectionRef", connectionRef)
                .param("now", now)
                .update();
    }
}
