package com.amateur.howmuchfinanceconnector.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amateur.howmuchfinanceconnector.adapter.out.codef.CodefBankClient;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.CollectionExecutionJpaEntity;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.CollectionExecutionJpaRepository;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.ConnectedAccountJpaEntity;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.ConnectedAccountJpaRepository;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.FinancialConnectionJpaEntity;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.FinancialConnectionJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CodefCollectionServiceTest {

    @Test
    void blocksNonEmptyResultsUntilProviderStableIdentityIsVerified() {
        UUID jobId = UUID.randomUUID();
        UUID connectionRef = UUID.randomUUID();
        UUID accountRef = UUID.randomUUID();
        CollectionExecutionJpaEntity execution = mock(CollectionExecutionJpaEntity.class);
        when(execution.getConnectionRef()).thenReturn(connectionRef);
        when(execution.getConnectionVersion()).thenReturn(1L);
        when(execution.getAccountRef()).thenReturn(accountRef);

        FinancialConnectionJpaEntity connection = mock(FinancialConnectionJpaEntity.class);
        when(connection.isActive()).thenReturn(true);
        when(connection.getConnectionVersion()).thenReturn(1);
        ConnectedAccountJpaEntity account = mock(ConnectedAccountJpaEntity.class);

        CollectionExecutionJpaRepository executions = mock(CollectionExecutionJpaRepository.class);
        FinancialConnectionJpaRepository connections = mock(FinancialConnectionJpaRepository.class);
        ConnectedAccountJpaRepository accounts = mock(ConnectedAccountJpaRepository.class);
        CodefBankClient client = mock(CodefBankClient.class);
        CollectionResultStore results = mock(CollectionResultStore.class);
        when(executions.findById(jobId)).thenReturn(Optional.of(execution));
        when(connections.findById(connectionRef)).thenReturn(Optional.of(connection));
        when(accounts.findByAccountRefAndConnectionRefAndActiveTrue(accountRef, connectionRef))
                .thenReturn(Optional.of(account));
        when(client.fetch(any(), any(), any(), any(), any()))
                .thenReturn(List.of(new ObjectMapper().createObjectNode().put("resAccountOut", "1000")));

        new CodefCollectionService(executions, connections, accounts, client, results).collect(jobId);

        verify(results).fail(
                eq(jobId),
                eq("DATA_IDENTITY_AMBIGUOUS"),
                eq("DATA_INVALID"),
                eq("CONTACT_SUPPORT"),
                any()
        );
    }
}
