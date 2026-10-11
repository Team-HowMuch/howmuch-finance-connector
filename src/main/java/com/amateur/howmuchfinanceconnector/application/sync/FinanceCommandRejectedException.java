package com.amateur.howmuchfinanceconnector.application.sync;

public final class FinanceCommandRejectedException extends RuntimeException {
    public FinanceCommandRejectedException(String message) {
        super(message);
    }
}
