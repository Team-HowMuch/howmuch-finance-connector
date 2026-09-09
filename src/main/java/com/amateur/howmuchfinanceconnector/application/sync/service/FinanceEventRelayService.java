package com.amateur.howmuchfinanceconnector.application.sync.service;

import com.amateur.howmuchfinanceconnector.application.port.in.sync.PublishNextFinanceEventUseCase;
import com.amateur.howmuchfinanceconnector.application.port.out.sync.FinanceEventOutboxStore;
import com.amateur.howmuchfinanceconnector.application.port.out.sync.FinanceEventPublisher;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class FinanceEventRelayService implements PublishNextFinanceEventUseCase {

    private final FinanceEventOutboxStore store;
    private final FinanceEventPublisher publisher;
    private final Clock clock;

    public FinanceEventRelayService(FinanceEventOutboxStore store, FinanceEventPublisher publisher, Clock clock) {
        this.store = store;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Override
    public boolean publishNext() {
        var event = store.claimNextEvent(now());
        if (event.isEmpty()) {
            return false;
        }
        publisher.publish(event.get());
        store.markPublished(event.get().messageId(), now());
        return true;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
