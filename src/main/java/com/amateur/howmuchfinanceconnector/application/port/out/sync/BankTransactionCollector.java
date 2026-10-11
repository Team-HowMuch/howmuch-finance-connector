package com.amateur.howmuchfinanceconnector.application.port.out.sync;

import com.amateur.howmuchfinanceconnector.domain.sync.CollectedBankTransaction;
import com.amateur.howmuchfinanceconnector.domain.sync.FinanceCollectionTask;

import java.util.List;

public interface BankTransactionCollector {
    List<CollectedBankTransaction> collect(FinanceCollectionTask task);
}
