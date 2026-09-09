package com.amateur.howmuchfinanceconnector.domain.sync;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record FinanceCollectionTask(
        UUID syncJobId,
        UUID commandMessageId,
        UUID accountRef,
        long syncVersion,
        UUID connectionRef,
        long connectionVersion,
        LocalDate fromDate,
        LocalDate toDate,
        LocalDateTime deadlineAt,
        String traceId
) {
}
