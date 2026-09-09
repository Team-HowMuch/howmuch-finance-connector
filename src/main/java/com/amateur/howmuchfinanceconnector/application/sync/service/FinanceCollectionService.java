package com.amateur.howmuchfinanceconnector.application.sync.service;

import com.amateur.howmuchfinanceconnector.application.port.in.sync.CollectNextFinanceExecutionUseCase;
import com.amateur.howmuchfinanceconnector.application.port.out.sync.BankTransactionCollector;
import com.amateur.howmuchfinanceconnector.application.port.out.sync.FinanceCollectionExecutionStore;
import com.amateur.howmuchfinanceconnector.application.sync.FinanceCollectionException;
import com.amateur.howmuchfinanceconnector.domain.sync.CollectedBankTransaction;
import com.amateur.howmuchfinanceconnector.domain.sync.FinanceCollectionFailureCode;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

public final class FinanceCollectionService implements CollectNextFinanceExecutionUseCase {

    private final FinanceCollectionExecutionStore store;
    private final BankTransactionCollector collector;
    private final Clock clock;

    public FinanceCollectionService(
            FinanceCollectionExecutionStore store,
            BankTransactionCollector collector,
            Clock clock
    ) {
        this.store = store;
        this.collector = collector;
        this.clock = clock;
    }

    @Override
    public boolean collectNext() {
        LocalDateTime now = now();
        var task = store.claimNextExecution(now);
        if (task.isEmpty()) {
            return false;
        }

        try {
            if (!task.get().deadlineAt().isAfter(now)) {
                throw failure(FinanceCollectionFailureCode.DEADLINE_EXCEEDED, false,
                        "거래 수집 제한 시간을 초과했습니다.");
            }
            List<CollectedBankTransaction> transactions = collector.collect(task.get());
            validate(transactions);
            store.complete(task.get(), transactions, now());
        } catch (FinanceCollectionException exception) {
            store.fail(task.get(), exception.failureCode(), exception.retryable(), exception.safeMessage(), now());
        } catch (RuntimeException exception) {
            store.fail(task.get(), FinanceCollectionFailureCode.INTERNAL_ERROR, false,
                    "거래 수집 처리 중 오류가 발생했습니다.", now());
        }
        return true;
    }

    private void validate(List<CollectedBankTransaction> transactions) {
        for (CollectedBankTransaction transaction : transactions) {
            if (transaction.providerTransactionId() == null
                    || transaction.providerTransactionId().isBlank()
                    || transaction.providerTransactionId().length() > 256) {
                throw failure(FinanceCollectionFailureCode.AMBIGUOUS, false,
                        "거래 식별자를 확인할 수 없어 수집 결과를 반영하지 않았습니다.");
            }
            if (transaction.bookedAt() == null
                    || transaction.direction() == null
                    || transaction.description() != null && transaction.description().length() > 500) {
                throw failure(FinanceCollectionFailureCode.INVALID_REQUEST, false,
                        "지원하지 않는 거래 일시 또는 설명 형식입니다.");
            }
            if (!"KRW".equals(transaction.currency())
                    || transaction.amount() == null
                    || transaction.amount().signum() <= 0
                    || transaction.amount().stripTrailingZeros().scale() > 0
                    || transaction.balanceAfter() != null
                    && transaction.balanceAfter().stripTrailingZeros().scale() > 0) {
                throw failure(FinanceCollectionFailureCode.INVALID_REQUEST, false,
                        "지원하지 않는 거래 금액 또는 통화 형식입니다.");
            }
        }
    }

    private FinanceCollectionException failure(
            FinanceCollectionFailureCode code,
            boolean retryable,
            String message
    ) {
        return new FinanceCollectionException(code, retryable, message);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
