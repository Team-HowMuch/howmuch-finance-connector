package com.amateur.howmuchfinanceconnector.application.port.in.connection;

import java.util.UUID;

public interface RevokeFinanceConnectionUseCase {
    void revoke(UUID connectionRef);
}
