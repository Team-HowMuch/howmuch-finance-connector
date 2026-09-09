package com.amateur.howmuchfinanceconnector.infra.connection.config;

import com.amateur.howmuchfinanceconnector.adapter.out.crypto.AesGcmConnectionSecretCipher;
import com.amateur.howmuchfinanceconnector.application.connection.service.FinanceConnectionService;
import com.amateur.howmuchfinanceconnector.application.port.in.connection.RegisterFinanceConnectionUseCase;
import com.amateur.howmuchfinanceconnector.application.port.in.connection.RevokeFinanceConnectionUseCase;
import com.amateur.howmuchfinanceconnector.application.port.out.connection.ConnectionSecretCipher;
import com.amateur.howmuchfinanceconnector.application.port.out.connection.FinanceConnectionStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@Configuration
public class FinanceConnectionConfig {

    @Bean
    Clock utcClock() {
        return Clock.systemUTC();
    }

    @Bean
    ConnectionSecretCipher connectionSecretCipher(@Value("${finance.internal.data-key}") String dataKey) {
        return new AesGcmConnectionSecretCipher(dataKey);
    }

    @Bean
    FinanceConnectionService financeConnectionService(
            FinanceConnectionStore store,
            ConnectionSecretCipher cipher,
            Clock utcClock
    ) {
        return new FinanceConnectionService(store, cipher, utcClock);
    }

    @Bean
    RegisterFinanceConnectionUseCase registerFinanceConnectionUseCase(
            FinanceConnectionService service,
            PlatformTransactionManager transactionManager
    ) {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        return command -> transactions.execute(status -> service.register(command));
    }

    @Bean
    RevokeFinanceConnectionUseCase revokeFinanceConnectionUseCase(
            FinanceConnectionService service,
            PlatformTransactionManager transactionManager
    ) {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        return connectionRef -> transactions.executeWithoutResult(status -> service.revoke(connectionRef));
    }
}
