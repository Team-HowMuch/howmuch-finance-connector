package com.amateur.howmuchfinanceconnector.application.port.out.sync;

import com.amateur.howmuchfinanceconnector.domain.sync.CollectedBankTransaction;
import com.amateur.howmuchfinanceconnector.domain.sync.FinanceCollectionFailureCode;
import com.amateur.howmuchfinanceconnector.domain.sync.FinanceCollectionTask;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FinanceCollectionExecutionStore {
    Optional<FinanceCollectionTask> claimNextExecution(LocalDateTime now);

    void complete(FinanceCollectionTask task, List<CollectedBankTransaction> transactions, LocalDateTime completedAt);

    void fail(
            FinanceCollectionTask task,
            FinanceCollectionFailureCode failureCode,
            boolean retryable,
            String safeMessage,
            LocalDateTime failedAt
    );
}
