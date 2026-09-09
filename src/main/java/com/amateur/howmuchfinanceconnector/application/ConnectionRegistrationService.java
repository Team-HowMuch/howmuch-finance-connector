package com.amateur.howmuchfinanceconnector.application;

import com.amateur.howmuchfinanceconnector.adapter.out.persistence.ConnectedAccountJpaEntity;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.ConnectedAccountJpaRepository;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.FinancialConnectionJpaEntity;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.FinancialConnectionJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConnectionRegistrationService {

    private final FinancialConnectionJpaRepository connectionRepository;
    private final ConnectedAccountJpaRepository accountRepository;
    private final ObjectMapper objectMapper;

    public ConnectionRegistrationService(
            FinancialConnectionJpaRepository connectionRepository,
            ConnectedAccountJpaRepository accountRepository,
            ObjectMapper objectMapper
    ) {
        this.connectionRepository = connectionRepository;
        this.accountRepository = accountRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void register(UUID connectionRef, ConnectionRegistrationRequest request) {
        String hash = requestHash(request);
        Instant now = Instant.now();
        FinancialConnectionJpaEntity connection = connectionRepository.findById(connectionRef).orElse(null);
        if (connection != null && connection.getConnectionVersion() == request.connectionVersion()) {
            if (!connection.getRequestHash().equals(hash)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "connection version payload conflict");
            }
            return;
        }
        if (connection != null && connection.getConnectionVersion() > request.connectionVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "stale connection version");
        }
        if (connection == null) {
            connection = new FinancialConnectionJpaEntity(
                    connectionRef,
                    request.connectionVersion(),
                    request.provider(),
                    request.institutionType(),
                    request.organizationCode(),
                    request.connectedId(),
                    hash,
                    now
            );
        } else {
            connection.update(
                    request.connectionVersion(),
                    request.provider(),
                    request.institutionType(),
                    request.organizationCode(),
                    request.connectedId(),
                    hash,
                    now
            );
        }
        connectionRepository.save(connection);

        Set<UUID> requestedAccounts = new HashSet<>();
        for (ConnectionRegistrationRequest.AccountRegistration account : request.accounts()) {
            requestedAccounts.add(account.accountRef());
            ConnectedAccountJpaEntity entity = accountRepository.findById(account.accountRef())
                    .orElseGet(() -> new ConnectedAccountJpaEntity(
                            account.accountRef(),
                            connectionRef,
                            account.providerAccountRef(),
                            now
                    ));
            if (!entity.getConnectionRef().equals(connectionRef)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "accountRef belongs to another connection");
            }
            entity.update(connectionRef, account.providerAccountRef(), now);
            accountRepository.save(entity);
        }
        accountRepository.findAllByConnectionRef(connectionRef).stream()
                .filter(account -> !requestedAccounts.contains(account.getAccountRef()))
                .forEach(account -> account.revoke(now));
    }

    @Transactional
    public void revoke(UUID connectionRef) {
        Instant now = Instant.now();
        connectionRepository.findById(connectionRef).ifPresent(connection -> {
            connection.revoke(now);
            accountRepository.findAllByConnectionRef(connectionRef)
                    .forEach(account -> account.revoke(now));
        });
    }

    private String requestHash(ConnectionRegistrationRequest request) {
        try {
            return MessageHash.sha256(objectMapper.writeValueAsString(request));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("connection request cannot be serialized", exception);
        }
    }
}
