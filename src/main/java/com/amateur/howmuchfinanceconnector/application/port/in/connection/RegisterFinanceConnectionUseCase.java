package com.amateur.howmuchfinanceconnector.application.port.in.connection;

public interface RegisterFinanceConnectionUseCase {
    FinanceConnectionRegistrationResult register(RegisterFinanceConnectionCommand command);
}
