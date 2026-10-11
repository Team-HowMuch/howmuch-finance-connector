package com.amateur.howmuchfinanceconnector.domain.sync;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CollectedBankTransaction(
        String providerTransactionId,
        LocalDateTime bookedAt,
        FinanceTransactionDirection direction,
        BigDecimal amount,
        String currency,
        BigDecimal balanceAfter,
        String description
) {
}
