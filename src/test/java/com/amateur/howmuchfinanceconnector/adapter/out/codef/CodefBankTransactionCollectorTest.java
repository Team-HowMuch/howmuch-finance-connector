package com.amateur.howmuchfinanceconnector.adapter.out.codef;

import com.amateur.howmuchfinanceconnector.application.port.out.connection.ConnectionSecretCipher;
import com.amateur.howmuchfinanceconnector.application.port.out.sync.FinanceCollectionCredentialStore;
import com.amateur.howmuchfinanceconnector.application.sync.FinanceCollectionException;
import com.amateur.howmuchfinanceconnector.domain.sync.FinanceCollectionFailureCode;
import com.amateur.howmuchfinanceconnector.domain.sync.FinanceCollectionTask;
import com.amateur.howmuchfinanceconnector.domain.sync.FinanceTransactionDirection;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodefBankTransactionCollectorTest {

    private final CodefClient client = mock(CodefClient.class);
    private final FinanceCollectionCredentialStore credentials = mock(FinanceCollectionCredentialStore.class);
    private final ConnectionSecretCipher cipher = mock(ConnectionSecretCipher.class);
    private final CodefBankTransactionCollector collector =
            new CodefBankTransactionCollector(client, credentials, cipher);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsVerifiedCodefFieldsWithoutInventingProviderTransactionId() {
        FinanceCollectionTask task = task();
        when(credentials.findActive(task.connectionRef(), task.accountRef(), task.connectionVersion()))
                .thenReturn(Optional.of(new FinanceCollectionCredentialStore.EncryptedBankCredential(
                        "encrypted-connected-id", "0088", "encrypted-account")));
        when(cipher.decrypt("encrypted-connected-id")).thenReturn("connected-id");
        when(cipher.decrypt("encrypted-account")).thenReturn("provider-account");
        when(client.post(eq("/v1/kr/bank/p/account/transaction-list"), any())).thenReturn(objectMapper.readTree("""
                {
                  "result": {"code": "CF-00000"},
                  "data": {"resTrHistoryList": [{
                    "resAccountTrDate": "20260909",
                    "resAccountTrTime": "090000",
                    "resAccountOut": "12,000",
                    "resAfterTranBalance": "988000",
                    "resAccountDesc1": "상점",
                    "resAccountDesc2": "결제"
                  }]}
                }
                """));

        var result = collector.collect(task);

        assertThat(result).singleElement().satisfies(transaction -> {
            assertThat(transaction.providerTransactionId()).isNull();
            assertThat(transaction.bookedAt()).isEqualTo(LocalDateTime.of(2026, 9, 9, 0, 0));
            assertThat(transaction.direction()).isEqualTo(FinanceTransactionDirection.EXPENSE);
            assertThat(transaction.amount()).isEqualByComparingTo("12000");
            assertThat(transaction.balanceAfter()).isEqualByComparingTo("988000");
            assertThat(transaction.description()).isEqualTo("상점 결제");
        });
        verify(client).post(eq("/v1/kr/bank/p/account/transaction-list"), eq(Map.of(
                "connectedId", "connected-id",
                "organization", "0088",
                "account", "provider-account",
                "startDate", "20260908",
                "endDate", "20260909",
                "orderBy", "1",
                "inquiryType", "1")));
    }

    @Test
    void mapsAdditionalAuthenticationAndRevokedConnectionToSafeFailures() {
        FinanceCollectionTask task = task();
        when(credentials.findActive(task.connectionRef(), task.accountRef(), task.connectionVersion()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> collector.collect(task))
                .isInstanceOfSatisfying(FinanceCollectionException.class,
                        exception -> assertThat(exception.failureCode())
                                .isEqualTo(FinanceCollectionFailureCode.CONNECTION_REVOKED));

        when(credentials.findActive(task.connectionRef(), task.accountRef(), task.connectionVersion()))
                .thenReturn(Optional.of(new FinanceCollectionCredentialStore.EncryptedBankCredential(
                        "connected", "0088", "account")));
        when(cipher.decrypt(any())).thenReturn("decrypted");
        when(client.post(eq("/v1/kr/bank/p/account/transaction-list"), any())).thenReturn(objectMapper.readTree("""
                {"result":{"code":"CF-00000"},"data":{"continue2Way":true}}
                """));

        assertThatThrownBy(() -> collector.collect(task))
                .isInstanceOfSatisfying(FinanceCollectionException.class,
                        exception -> assertThat(exception.failureCode())
                                .isEqualTo(FinanceCollectionFailureCode.REAUTH_REQUIRED));
    }

    @Test
    void rejectsMissingHistoryAndInvalidItemsInsteadOfReportingAnEmptyCollection() {
        FinanceCollectionTask task = task();
        when(credentials.findActive(task.connectionRef(), task.accountRef(), task.connectionVersion()))
                .thenReturn(Optional.of(new FinanceCollectionCredentialStore.EncryptedBankCredential(
                        "connected", "0088", "account")));
        when(cipher.decrypt(any())).thenReturn("decrypted");
        when(client.post(eq("/v1/kr/bank/p/account/transaction-list"), any()))
                .thenReturn(objectMapper.readTree("""
                        {"result":{"code":"CF-00000"},"data":{}}
                        """))
                .thenReturn(objectMapper.readTree("""
                        {"result":{"code":"CF-00000"},"data":{"resTrHistoryList":[{"resAccountTrDate":"bad"}]}}
                        """))
                .thenReturn(objectMapper.readTree("""
                        {"result":{"code":"CF-00000"},"data":{"resTrHistoryList":[]}}
                        """));

        assertThatThrownBy(() -> collector.collect(task))
                .isInstanceOfSatisfying(FinanceCollectionException.class,
                        exception -> assertThat(exception.failureCode())
                                .isEqualTo(FinanceCollectionFailureCode.INVALID_REQUEST));
        assertThatThrownBy(() -> collector.collect(task))
                .isInstanceOfSatisfying(FinanceCollectionException.class,
                        exception -> assertThat(exception.failureCode())
                                .isEqualTo(FinanceCollectionFailureCode.INVALID_REQUEST));
        assertThat(collector.collect(task)).isEmpty();
    }

    private FinanceCollectionTask task() {
        return new FinanceCollectionTask(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                UUID.fromString("22222222-2222-4222-8222-222222222222"),
                UUID.fromString("33333333-3333-4333-8333-333333333333"),
                1,
                UUID.fromString("44444444-4444-4444-8444-444444444444"),
                1,
                LocalDate.of(2026, 9, 8),
                LocalDate.of(2026, 9, 9),
                LocalDateTime.of(2026, 9, 9, 0, 10),
                "trace-id");
    }
}
