package com.amateur.howmuchfinanceconnector.application.port.out.sync;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface FinanceCommandIntakeStore {

    void lock(UUID messageId, UUID syncJobId);

    Optional<InboxSnapshot> findInbox(UUID messageId);

    void insertInbox(ReceivedCommand command);

    Optional<ExecutionSnapshot> findExecution(UUID syncJobId);

    Optional<ActiveAccountSnapshot> findActiveAccount(UUID accountRef);

    void markProcessed(UUID messageId, LocalDateTime processedAt);

    void createAcceptedExecution(AcceptedExecution execution);

    record InboxSnapshot(String payloadHash) {
    }

    record ExecutionSnapshot(String commandHash) {
    }

    record ActiveAccountSnapshot(UUID connectionRef, long connectionVersion) {
    }

    record ReceivedCommand(
            UUID messageId,
            UUID syncJobId,
            UUID accountRef,
            String payloadHash,
            LocalDateTime receivedAt
    ) {
    }

    record AcceptedExecution(
            UUID messageId,
            UUID syncJobId,
            UUID accountRef,
            long syncVersion,
            long connectionVersion,
            UUID connectionRef,
            String commandHash,
            LocalDate fromDate,
            LocalDate toDate,
            LocalDateTime deadlineAt,
            String traceId,
            UUID eventMessageId,
            LocalDateTime acceptedAt
    ) {
    }
}
