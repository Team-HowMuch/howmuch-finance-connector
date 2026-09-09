package com.amateur.howmuchfinanceconnector.application.port.out.sync;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface FinanceEventOutboxStore {
    Optional<PendingFinanceEvent> claimNextEvent(LocalDateTime now);

    void markPublished(UUID messageId, LocalDateTime publishedAt);

    record PendingFinanceEvent(UUID messageId, UUID accountRef, String topic, String payload) {
    }
}
