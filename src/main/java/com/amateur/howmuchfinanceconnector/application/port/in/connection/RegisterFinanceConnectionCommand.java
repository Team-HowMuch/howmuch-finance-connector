package com.amateur.howmuchfinanceconnector.application.port.in.connection;

import com.amateur.howmuchfinanceconnector.domain.connection.FinanceAccountType;

import java.util.List;
import java.util.UUID;

public record RegisterFinanceConnectionCommand(
        UUID connectionRef,
        String connectedId,
        String organizationCode,
        List<Account> accounts
) {
    public record Account(
            UUID accountRef,
            String providerAccountRef,
            FinanceAccountType accountType,
            String productCode,
            String currency
    ) {
    }
}
