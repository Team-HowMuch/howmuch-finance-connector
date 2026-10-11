package com.amateur.howmuchfinanceconnector.infra.sync.config;

import com.amateur.howmuchfinanceconnector.application.port.in.sync.AcceptFinanceSyncCommandUseCase;
import com.amateur.howmuchfinanceconnector.application.port.out.sync.FinanceCommandIntakeStore;
import com.amateur.howmuchfinanceconnector.application.sync.service.FinanceCommandIntakeService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@Configuration
public class FinanceCommandIntakeConfig {

    @Bean
    FinanceCommandIntakeService financeCommandIntakeService(FinanceCommandIntakeStore store, Clock utcClock) {
        return new FinanceCommandIntakeService(store, utcClock);
    }

    @Bean
    AcceptFinanceSyncCommandUseCase acceptFinanceSyncCommandUseCase(
            FinanceCommandIntakeService service,
            PlatformTransactionManager transactionManager
    ) {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        return command -> transactions.executeWithoutResult(status -> service.accept(command));
    }
}
