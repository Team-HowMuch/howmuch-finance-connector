package com.amateur.howmuchfinanceconnector.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FinanceSyncCommand(
        UUID messageId,
        Instant occurredAt,
        UUID syncJobId,
        UUID accountRef,
        long syncVersion,
        long connectionVersion,
        String traceId,
        UUID connectionRef,
        LocalDate fromDate,
        LocalDate toDate,
        Instant deadlineAt
) {
    String requestIdentity() {
        return String.join("|",
                syncJobId.toString(),
                accountRef.toString(),
                Long.toString(syncVersion),
                Long.toString(connectionVersion),
                connectionRef.toString(),
                fromDate.toString(),
                toDate.toString(),
                deadlineAt.toString()
        );
    }
}
