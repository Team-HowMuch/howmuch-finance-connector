package com.amateur.howmuchfinanceconnector.application.connection;

public final class FinanceConnectionAccountConflictException extends RuntimeException {
    public FinanceConnectionAccountConflictException() {
        super("계좌 참조가 다른 연결에 이미 등록되어 있습니다.");
    }
}
