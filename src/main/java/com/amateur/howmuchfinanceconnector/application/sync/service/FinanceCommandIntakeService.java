package com.amateur.howmuchfinanceconnector.application.sync.service;

import com.amateur.howmuchfinanceconnector.application.port.in.sync.AcceptFinanceSyncCommand;
import com.amateur.howmuchfinanceconnector.application.port.out.sync.FinanceCommandIntakeStore;
import com.amateur.howmuchfinanceconnector.application.sync.FinanceCommandConflictException;
import com.amateur.howmuchfinanceconnector.application.sync.FinanceCommandRejectedException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public final class FinanceCommandIntakeService {

    private final FinanceCommandIntakeStore store;
    private final Clock clock;

    public FinanceCommandIntakeService(FinanceCommandIntakeStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public void accept(AcceptFinanceSyncCommand command) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (!command.deadlineAt().isAfter(now)) {
            throw new FinanceCommandRejectedException("수집 명령의 deadline이 지났습니다.");
        }

        store.lock(command.messageId(), command.syncJobId());
        var inbox = store.findInbox(command.messageId());
        if (inbox.isPresent()) {
            if (!inbox.get().payloadHash().equals(command.payloadHash())) {
                throw new FinanceCommandConflictException("같은 messageId에 다른 payload가 전달되었습니다.");
            }
            return;
        }

        store.insertInbox(new FinanceCommandIntakeStore.ReceivedCommand(
                command.messageId(), command.syncJobId(), command.accountRef(), command.payloadHash(), now));

        var execution = store.findExecution(command.syncJobId());
        if (execution.isPresent()) {
            if (!execution.get().commandHash().equals(command.commandHash())) {
                throw new FinanceCommandConflictException("같은 syncJobId에 다른 command가 전달되었습니다.");
            }
            store.markProcessed(command.messageId(), now);
            return;
        }

        var account = store.findActiveAccount(command.accountRef())
                .orElseThrow(() -> new FinanceCommandRejectedException("활성 금융 계좌 연결을 찾을 수 없습니다."));
        if (!account.connectionRef().equals(command.connectionRef())
                || account.connectionVersion() != command.connectionVersion()) {
            throw new FinanceCommandRejectedException("금융 연결 version이 현재 등록 정보와 일치하지 않습니다.");
        }

        store.createAcceptedExecution(new FinanceCommandIntakeStore.AcceptedExecution(
                command.messageId(),
                command.syncJobId(),
                command.accountRef(),
                command.syncVersion(),
                command.connectionVersion(),
                command.connectionRef(),
                command.commandHash(),
                command.fromDate(),
                command.toDate(),
                command.deadlineAt(),
                command.traceId(),
                UUID.randomUUID(),
                now));
    }
}
