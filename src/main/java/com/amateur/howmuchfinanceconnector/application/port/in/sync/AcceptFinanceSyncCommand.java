package com.amateur.howmuchfinanceconnector.application.port.in.sync;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record AcceptFinanceSyncCommand(
        UUID messageId,
        UUID syncJobId,
        UUID accountRef,
        long syncVersion,
        long connectionVersion,
        UUID connectionRef,
        LocalDate fromDate,
        LocalDate toDate,
        LocalDateTime deadlineAt,
        String traceId,
        String payloadHash,
        String commandHash
) {
}
