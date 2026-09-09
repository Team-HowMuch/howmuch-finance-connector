package com.amateur.howmuchfinanceconnector.adapter.in.scheduler;

import com.amateur.howmuchfinanceconnector.application.port.in.sync.CollectNextFinanceExecutionUseCase;
import com.amateur.howmuchfinanceconnector.application.port.in.sync.PublishNextFinanceEventUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

public final class FinanceCollectionScheduler {

    private FinanceCollectionScheduler() {
    }

    @Component
    @ConditionalOnProperty(name = "finance.collection.enabled", havingValue = "true")
    static class CollectionWorker {
        private final CollectNextFinanceExecutionUseCase useCase;

        CollectionWorker(CollectNextFinanceExecutionUseCase useCase) {
            this.useCase = useCase;
        }

        @Scheduled(fixedDelayString = "${finance.collection.poll-delay:500}")
        void collect() {
            while (useCase.collectNext()) {
                // Drain currently ready work before yielding the scheduler thread.
            }
        }
    }

    @Component
    @ConditionalOnProperty(name = "finance.event-relay.enabled", havingValue = "true")
    static class EventRelay {
        private final PublishNextFinanceEventUseCase useCase;

        EventRelay(PublishNextFinanceEventUseCase useCase) {
            this.useCase = useCase;
        }

        @Scheduled(fixedDelayString = "${finance.event-relay.poll-delay:200}")
        void publish() {
            while (useCase.publishNext()) {
                // Preserve outbox creation order with one relay thread per instance.
            }
        }
    }
}
