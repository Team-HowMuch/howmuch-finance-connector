package com.amateur.howmuchfinanceconnector.application.connection.service;

import com.amateur.howmuchfinanceconnector.application.connection.FinanceConnectionAccountConflictException;
import com.amateur.howmuchfinanceconnector.application.port.in.connection.FinanceConnectionRegistrationResult;
import com.amateur.howmuchfinanceconnector.application.port.in.connection.RegisterFinanceConnectionCommand;
import com.amateur.howmuchfinanceconnector.application.port.out.connection.ConnectionSecretCipher;
import com.amateur.howmuchfinanceconnector.application.port.out.connection.FinanceConnectionStore;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;

public final class FinanceConnectionService {

    private final FinanceConnectionStore store;
    private final ConnectionSecretCipher cipher;
    private final Clock clock;

    public FinanceConnectionService(FinanceConnectionStore store, ConnectionSecretCipher cipher, Clock clock) {
        this.store = store;
        this.cipher = cipher;
        this.clock = clock;
    }

    public FinanceConnectionRegistrationResult register(RegisterFinanceConnectionCommand command) {
        var normalized = normalize(command);
        String registrationHash = fingerprint(normalized);

        store.lock(normalized.connectionRef());
        var current = store.find(normalized.connectionRef());
        if (current.isPresent()
                && current.get().active()
                && current.get().registrationHash().equals(registrationHash)) {
            return new FinanceConnectionRegistrationResult(
                    normalized.connectionRef(), current.get().version(), false);
        }

        for (var account : normalized.accounts()) {
            store.findOwningConnection(account.accountRef())
                    .filter(owner -> !owner.equals(normalized.connectionRef()))
                    .ifPresent(owner -> {
                        throw new FinanceConnectionAccountConflictException();
                    });
        }

        long version = current.map(snapshot -> snapshot.version() + 1).orElse(1L);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        List<FinanceConnectionStore.StoredAccount> accounts = normalized.accounts().stream()
                .map(account -> new FinanceConnectionStore.StoredAccount(
                        account.accountRef(),
                        cipher.encrypt(account.providerAccountRef()),
                        account.accountType(),
                        account.productCode(),
                        account.currency()))
                .toList();
        store.save(new FinanceConnectionStore.StoredConnection(
                normalized.connectionRef(),
                version,
                normalized.organizationCode(),
                cipher.encrypt(normalized.connectedId()),
                registrationHash,
                accounts,
                now));
        return new FinanceConnectionRegistrationResult(normalized.connectionRef(), version, true);
    }

    public void revoke(java.util.UUID connectionRef) {
        store.lock(connectionRef);
        store.revoke(connectionRef, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }

    private RegisterFinanceConnectionCommand normalize(RegisterFinanceConnectionCommand command) {
        var accountIds = new HashSet<java.util.UUID>();
        List<RegisterFinanceConnectionCommand.Account> accounts = command.accounts().stream()
                .map(account -> new RegisterFinanceConnectionCommand.Account(
                        account.accountRef(),
                        account.providerAccountRef().trim(),
                        account.accountType(),
                        account.productCode().trim(),
                        account.currency().trim()))
                .sorted(Comparator.comparing(account -> account.accountRef().toString()))
                .toList();
        if (accounts.stream().anyMatch(account -> !accountIds.add(account.accountRef()))) {
            throw new IllegalArgumentException("accountRef는 요청 안에서 중복될 수 없습니다.");
        }
        return new RegisterFinanceConnectionCommand(
                command.connectionRef(), command.connectedId().trim(), command.organizationCode().trim(), accounts);
    }

    private String fingerprint(RegisterFinanceConnectionCommand command) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, command.connectedId());
            update(digest, command.organizationCode());
            for (var account : command.accounts()) {
                update(digest, account.accountRef().toString());
                update(digest, account.providerAccountRef());
                update(digest, account.accountType().name());
                update(digest, account.productCode());
                update(digest, account.currency());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
