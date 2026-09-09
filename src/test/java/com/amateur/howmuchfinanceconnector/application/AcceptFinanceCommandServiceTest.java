package com.amateur.howmuchfinanceconnector.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amateur.howmuchfinanceconnector.adapter.out.persistence.CollectionExecutionJpaEntity;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.CollectionExecutionJpaRepository;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.ConnectedAccountJpaRepository;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.FinancialConnectionJpaRepository;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.OutboxEventJpaRepository;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.ProcessedMessageJpaEntity;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.ProcessedMessageJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AcceptFinanceCommandServiceTest {

    @Test
    void recordsANewDeliveryOfTheSameLogicalJobWithoutCreatingAnotherExecution() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        FinanceCommandParser parser = new FinanceCommandParser(mapper);
        String original = resource("examples/sync-account-transactions.json");
        String redelivery = original.replace(
                "11111111-1111-4111-8111-111111111111",
                "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        );
        FinanceSyncCommand command = parser.parse(redelivery);

        ProcessedMessageJpaRepository processed = mock(ProcessedMessageJpaRepository.class);
        CollectionExecutionJpaRepository executions = mock(CollectionExecutionJpaRepository.class);
        CollectionExecutionJpaEntity existing = mock(CollectionExecutionJpaEntity.class);
        when(existing.getRequestHash()).thenReturn(MessageHash.sha256(command.requestIdentity()));
        when(executions.findById(command.syncJobId())).thenReturn(Optional.of(existing));

        var service = new AcceptFinanceCommandService(
                parser,
                mapper,
                processed,
                executions,
                mock(FinancialConnectionJpaRepository.class),
                mock(ConnectedAccountJpaRepository.class),
                mock(OutboxEventJpaRepository.class),
                "finance.sync.event.v1"
        );

        service.accept(redelivery);

        verify(processed).save(any(ProcessedMessageJpaEntity.class));
    }

    private String resource(String path) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            if (input == null) throw new IllegalArgumentException("missing resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
