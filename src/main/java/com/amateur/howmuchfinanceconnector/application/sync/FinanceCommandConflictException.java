package com.amateur.howmuchfinanceconnector.application.sync;

public final class FinanceCommandConflictException extends RuntimeException {
    public FinanceCommandConflictException(String message) {
        super(message);
    }
}
