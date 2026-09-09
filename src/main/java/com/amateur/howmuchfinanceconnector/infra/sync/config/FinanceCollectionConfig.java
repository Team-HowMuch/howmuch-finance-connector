package com.amateur.howmuchfinanceconnector.infra.sync.config;

import com.amateur.howmuchfinanceconnector.application.port.in.sync.CollectNextFinanceExecutionUseCase;
import com.amateur.howmuchfinanceconnector.application.port.in.sync.PublishNextFinanceEventUseCase;
import com.amateur.howmuchfinanceconnector.application.port.out.sync.BankTransactionCollector;
import com.amateur.howmuchfinanceconnector.application.port.out.sync.FinanceCollectionExecutionStore;
import com.amateur.howmuchfinanceconnector.application.port.out.sync.FinanceEventOutboxStore;
import com.amateur.howmuchfinanceconnector.application.port.out.sync.FinanceEventPublisher;
import com.amateur.howmuchfinanceconnector.application.sync.service.FinanceCollectionService;
import com.amateur.howmuchfinanceconnector.application.sync.service.FinanceEventRelayService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class FinanceCollectionConfig {

    @Bean
    CollectNextFinanceExecutionUseCase collectNextFinanceExecutionUseCase(
            FinanceCollectionExecutionStore store,
            BankTransactionCollector collector,
            Clock utcClock
    ) {
        return new FinanceCollectionService(store, collector, utcClock);
    }

    @Bean
    PublishNextFinanceEventUseCase publishNextFinanceEventUseCase(
            FinanceEventOutboxStore store,
            FinanceEventPublisher publisher,
            Clock utcClock
    ) {
        return new FinanceEventRelayService(store, publisher, utcClock);
    }
}
