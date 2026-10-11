package com.amateur.howmuchfinanceconnector.adapter.in.web.connection;

import com.amateur.howmuchfinanceconnector.application.port.in.connection.FinanceConnectionRegistrationResult;

import java.util.UUID;

public record FinanceConnectionRegistrationResponse(
        UUID connectionRef,
        long connectionVersion,
        boolean changed
) {
    static FinanceConnectionRegistrationResponse from(FinanceConnectionRegistrationResult result) {
        return new FinanceConnectionRegistrationResponse(
                result.connectionRef(), result.connectionVersion(), result.changed());
    }
}
