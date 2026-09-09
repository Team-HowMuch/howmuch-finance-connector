package com.amateur.howmuchfinanceconnector.application.sync.service;

import com.amateur.howmuchfinanceconnector.application.port.out.sync.BankTransactionCollector;
import com.amateur.howmuchfinanceconnector.application.port.out.sync.FinanceCollectionExecutionStore;
import com.amateur.howmuchfinanceconnector.domain.sync.CollectedBankTransaction;
import com.amateur.howmuchfinanceconnector.domain.sync.FinanceCollectionFailureCode;
import com.amateur.howmuchfinanceconnector.domain.sync.FinanceCollectionTask;
import com.amateur.howmuchfinanceconnector.domain.sync.FinanceTransactionDirection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinanceCollectionServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-09T00:00:00Z"), ZoneOffset.UTC);

    private final FinanceCollectionExecutionStore store = mock(FinanceCollectionExecutionStore.class);
    private final BankTransactionCollector collector = mock(BankTransactionCollector.class);
    private final FinanceCollectionService service = new FinanceCollectionService(store, collector, CLOCK);

    @Test
    void completesEmptyAndStableResultsButRejectsMissingProviderIdentity() {
        FinanceCollectionTask task = task(LocalDateTime.of(2026, 9, 9, 0, 10));
        when(store.claimNextExecution(any())).thenReturn(Optional.of(task));
        when(collector.collect(task)).thenReturn(List.of());

        service.collectNext();

        verify(store).complete(eq(task), eq(List.of()), any());

        var ambiguousStore = mock(FinanceCollectionExecutionStore.class);
        var ambiguousCollector = mock(BankTransactionCollector.class);
        var ambiguousService = new FinanceCollectionService(ambiguousStore, ambiguousCollector, CLOCK);
        when(ambiguousStore.claimNextExecution(any())).thenReturn(Optional.of(task));
        when(ambiguousCollector.collect(task)).thenReturn(List.of(transaction(null)));

        ambiguousService.collectNext();

        verify(ambiguousStore).fail(eq(task), eq(FinanceCollectionFailureCode.AMBIGUOUS), eq(false), any(), any());
        verify(ambiguousStore, never()).complete(any(), any(), any());

        var stableStore = mock(FinanceCollectionExecutionStore.class);
        var stableCollector = mock(BankTransactionCollector.class);
        var stableService = new FinanceCollectionService(stableStore, stableCollector, CLOCK);
        var stable = List.of(transaction("provider-transaction-id"));
        when(stableStore.claimNextExecution(any())).thenReturn(Optional.of(task));
        when(stableCollector.collect(task)).thenReturn(stable);

        stableService.collectNext();

        verify(stableStore).complete(eq(task), eq(stable), any());
        verify(stableStore, never()).fail(any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void rejectsExpiredExecutionBeforeExternalCall() {
        FinanceCollectionTask task = task(LocalDateTime.of(2026, 9, 9, 0, 0));
        when(store.claimNextExecution(any())).thenReturn(Optional.of(task));

        service.collectNext();

        verify(collector, never()).collect(any());
        verify(store).fail(eq(task), eq(FinanceCollectionFailureCode.DEADLINE_EXCEEDED), eq(false), any(), any());
    }

    @Test
    void rejectsResultWhenDeadlineExpiresDuringExternalCall() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(
                Instant.parse("2026-09-09T00:00:00Z"),
                Instant.parse("2026-09-09T00:11:00Z"),
                Instant.parse("2026-09-09T00:11:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        var deadlineStore = mock(FinanceCollectionExecutionStore.class);
        var deadlineCollector = mock(BankTransactionCollector.class);
        var deadlineService = new FinanceCollectionService(deadlineStore, deadlineCollector, clock);
        FinanceCollectionTask task = task(LocalDateTime.of(2026, 9, 9, 0, 10));
        when(deadlineStore.claimNextExecution(any())).thenReturn(Optional.of(task));
        when(deadlineCollector.collect(task)).thenReturn(List.of(transaction("provider-id")));

        deadlineService.collectNext();

        verify(deadlineStore, never()).complete(any(), any(), any());
        verify(deadlineStore).fail(eq(task), eq(FinanceCollectionFailureCode.DEADLINE_EXCEEDED),
                eq(false), any(), any());
    }

    private FinanceCollectionTask task(LocalDateTime deadlineAt) {
        return new FinanceCollectionTask(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                UUID.randomUUID(), 1,
                LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 9),
                deadlineAt, "trace-id");
    }

    private CollectedBankTransaction transaction(String providerTransactionId) {
        return new CollectedBankTransaction(
                providerTransactionId,
                LocalDateTime.of(2026, 9, 9, 0, 0),
                FinanceTransactionDirection.EXPENSE,
                new BigDecimal("1000"),
                "KRW",
                new BigDecimal("9000"),
                "결제");
    }
}
