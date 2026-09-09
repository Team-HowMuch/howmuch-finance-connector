package com.amateur.howmuchfinanceconnector.application.sync;

import com.amateur.howmuchfinanceconnector.domain.sync.FinanceCollectionFailureCode;

public final class FinanceCollectionException extends RuntimeException {
    private final FinanceCollectionFailureCode failureCode;
    private final boolean retryable;
    private final String safeMessage;

    public FinanceCollectionException(
            FinanceCollectionFailureCode failureCode,
            boolean retryable,
            String safeMessage
    ) {
        super(safeMessage);
        this.failureCode = failureCode;
        this.retryable = retryable;
        this.safeMessage = safeMessage;
    }

    public FinanceCollectionFailureCode failureCode() {
        return failureCode;
    }

    public boolean retryable() {
        return retryable;
    }

    public String safeMessage() {
        return safeMessage;
    }
}
