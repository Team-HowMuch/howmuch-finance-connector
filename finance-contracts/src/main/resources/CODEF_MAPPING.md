# CODEF bank transaction mapping gate

## Verified response shape

The Backend V2 fixture and CODEF's public Java sample expose these fields for each `resTrHistoryList` item:

- `resAccountTrDate`
- `resAccountTrTime`
- `resAccountOut`
- `resAccountIn`
- `resAfterTranBalance`
- `resAccountDesc1` through `resAccountDesc4`

The public sample also exposes `result.transactionId`, but that value identifies the API response, not one transaction item. The reviewed sources do not expose a provider-stable transaction identifier or a transaction-list pagination cursor.

## Mandatory gate

The connector must not derive identity from date, amount, balance, or description. Until an authorized development response proves a provider-stable identifier for every collected transaction, it must publish `TransactionsSyncFailed` with `failureCode=AMBIGUOUS`, publish no `TransactionsChunkCollected`, and keep the real-account canary disabled.

When a stable identifier is proven, `transactionKey` is the lowercase hexadecimal SHA-256 of the UTF-8 concatenation `accountRef + providerTransactionId`.

## Evidence

- Backend V2: `CodefBankTransactionAdapter` and `CodefBankTransactionAdapterTest`
- CODEF public Java sample: https://github.com/codef-io/codef-java
