package com.amateur.howmuchfinanceconnector.application.port.out.sync;

public interface FinanceEventPublisher {
    void publish(FinanceEventOutboxStore.PendingFinanceEvent event);
}
