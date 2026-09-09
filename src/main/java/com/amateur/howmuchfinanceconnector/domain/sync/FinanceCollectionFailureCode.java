package com.amateur.howmuchfinanceconnector.domain.sync;

public enum FinanceCollectionFailureCode {
    AMBIGUOUS,
    REAUTH_REQUIRED,
    UPSTREAM_UNAVAILABLE,
    INVALID_REQUEST,
    CONNECTION_REVOKED,
    DEADLINE_EXCEEDED,
    INTERNAL_ERROR
}
