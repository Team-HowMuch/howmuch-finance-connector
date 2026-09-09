package com.amateur.howmuchfinanceconnector.adapter.in.web.connection;

import com.amateur.howmuchfinanceconnector.application.port.in.connection.RegisterFinanceConnectionCommand;
import com.amateur.howmuchfinanceconnector.domain.connection.FinanceAccountType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

public record RegisterFinanceConnectionRequest(
        @NotBlank String connectedId,
        @NotBlank String organizationCode,
        @NotEmpty List<@Valid Account> accounts
) {
    RegisterFinanceConnectionCommand toCommand(UUID connectionRef) {
        return new RegisterFinanceConnectionCommand(
                connectionRef,
                connectedId,
                organizationCode,
                accounts.stream().map(Account::toCommand).toList());
    }

    public record Account(
            @NotNull UUID accountRef,
            @NotBlank String providerAccountRef,
            @NotNull FinanceAccountType accountType,
            @NotBlank String productCode,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency
    ) {
        RegisterFinanceConnectionCommand.Account toCommand() {
            return new RegisterFinanceConnectionCommand.Account(
                    accountRef, providerAccountRef, accountType, productCode, currency);
        }
    }
}
