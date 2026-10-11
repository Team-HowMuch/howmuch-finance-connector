package com.amateur.howmuchfinanceconnector.application.port.in.connection;

import java.util.UUID;

public record FinanceConnectionRegistrationResult(
        UUID connectionRef,
        long connectionVersion,
        boolean changed
) {
}
