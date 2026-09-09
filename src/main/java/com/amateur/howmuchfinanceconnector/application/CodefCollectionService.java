package com.amateur.howmuchfinanceconnector.application;

import com.amateur.howmuchfinanceconnector.adapter.out.codef.CodefBankClient;
import com.amateur.howmuchfinanceconnector.adapter.out.codef.CodefBankClient.CodefCollectionException;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.CollectionExecutionJpaEntity;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.CollectionExecutionJpaRepository;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.ConnectedAccountJpaRepository;
import com.amateur.howmuchfinanceconnector.adapter.out.persistence.FinancialConnectionJpaRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CodefCollectionService {

    private final CollectionExecutionJpaRepository executions;
    private final FinancialConnectionJpaRepository connections;
    private final ConnectedAccountJpaRepository accounts;
    private final CodefBankClient codef;
    private final CollectionResultStore results;

    public CodefCollectionService(
            CollectionExecutionJpaRepository executions,
            FinancialConnectionJpaRepository connections,
            ConnectedAccountJpaRepository accounts,
            CodefBankClient codef,
            CollectionResultStore results
    ) {
        this.executions = executions;
        this.connections = connections;
        this.accounts = accounts;
        this.codef = codef;
        this.results = results;
    }

    public void collect(UUID syncJobId) {
        CollectionExecutionJpaEntity execution = executions.findById(syncJobId)
                .orElseThrow(() -> new IllegalStateException("collection execution not found"));
        var connection = connections.findById(execution.getConnectionRef())
                .filter(value -> value.isActive() && value.getConnectionVersion() == execution.getConnectionVersion())
                .orElseThrow(() -> new IllegalStateException("connection is not active"));
        var account = accounts.findByAccountRefAndConnectionRefAndActiveTrue(
                        execution.getAccountRef(), execution.getConnectionRef())
                .orElseThrow(() -> new IllegalStateException("account is not active"));
        try {
            var transactions = codef.fetch(
                    connection.getConnectedId(),
                    connection.getOrganizationCode(),
                    account.getProviderAccountRef(),
                    execution.getFromDate(),
                    execution.getToDate()
            );
            if (!transactions.isEmpty()) {
                results.fail(
                        syncJobId,
                        "DATA_IDENTITY_AMBIGUOUS",
                        "DATA_INVALID",
                        "CONTACT_SUPPORT",
                        "안정적인 거래 식별자를 확인할 수 없어 동기화를 중단했습니다."
                );
                return;
            }
            results.completeEmpty(syncJobId);
        } catch (CodefCollectionException exception) {
            boolean reauth = exception.getMessage().equals("CF-12801") || exception.getMessage().equals("CF-12834");
            results.fail(
                    syncJobId,
                    reauth ? "AUTH_RECONNECT_REQUIRED" : exception.getMessage(),
                    reauth ? "USER_ACTION_REQUIRED" : "TEMPORARY",
                    reauth ? "RECONNECT" : "RETRY",
                    reauth ? "금융 연결을 다시 인증해 주세요." : "금융기관 조회에 실패했습니다. 잠시 후 다시 시도해 주세요."
            );
        }
    }
}
