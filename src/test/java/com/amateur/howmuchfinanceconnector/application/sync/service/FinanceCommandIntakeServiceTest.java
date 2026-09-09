package com.amateur.howmuchfinanceconnector.application.sync.service;

import com.amateur.howmuchfinanceconnector.application.port.in.sync.AcceptFinanceSyncCommand;
import com.amateur.howmuchfinanceconnector.application.port.out.sync.FinanceCommandIntakeStore;
import com.amateur.howmuchfinanceconnector.application.sync.FinanceCommandConflictException;
import com.amateur.howmuchfinanceconnector.application.sync.FinanceCommandRejectedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceCommandIntakeServiceTest {

    private static final UUID MESSAGE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID JOB_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID ACCOUNT_REF = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID CONNECTION_REF = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Mock
    private FinanceCommandIntakeStore store;

    @Test
    void rejectsMessageIdReuseWithDifferentPayload() {
        when(store.findInbox(MESSAGE_ID))
                .thenReturn(Optional.of(new FinanceCommandIntakeStore.InboxSnapshot("different")));

        assertThatThrownBy(() -> service().accept(command(1)))
                .isInstanceOf(FinanceCommandConflictException.class);

        verify(store, never()).createAcceptedExecution(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsStaleConnectionVersion() {
        when(store.findInbox(MESSAGE_ID)).thenReturn(Optional.empty());
        when(store.findExecution(JOB_ID)).thenReturn(Optional.empty());
        when(store.findActiveAccount(ACCOUNT_REF))
                .thenReturn(Optional.of(new FinanceCommandIntakeStore.ActiveAccountSnapshot(CONNECTION_REF, 2)));

        assertThatThrownBy(() -> service().accept(command(1)))
                .isInstanceOf(FinanceCommandRejectedException.class)
                .hasMessageContaining("version");

        verify(store, never()).createAcceptedExecution(org.mockito.ArgumentMatchers.any());
    }

    private FinanceCommandIntakeService service() {
        return new FinanceCommandIntakeService(
                store,
                Clock.fixed(Instant.parse("2026-09-09T00:00:00Z"), ZoneOffset.UTC));
    }

    private AcceptFinanceSyncCommand command(long connectionVersion) {
        return new AcceptFinanceSyncCommand(
                MESSAGE_ID,
                JOB_ID,
                ACCOUNT_REF,
                1,
                connectionVersion,
                CONNECTION_REF,
                LocalDate.parse("2026-09-01"),
                LocalDate.parse("2026-09-09"),
                LocalDateTime.parse("2026-09-09T00:10:00"),
                "test-trace",
                "payload-hash",
                "command-hash");
    }
}
