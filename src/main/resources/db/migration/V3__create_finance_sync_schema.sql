CREATE TABLE financial_connections (
    connection_ref UUID NOT NULL,
    connection_version BIGINT NOT NULL,
    provider VARCHAR(30) NOT NULL,
    institution_type VARCHAR(30) NOT NULL,
    organization_code VARCHAR(50) NOT NULL,
    connected_id_ciphertext TEXT NOT NULL,
    registration_hash VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_financial_connections PRIMARY KEY (connection_ref),
    CONSTRAINT ck_financial_connections_version
        CHECK (connection_version > 0),
    CONSTRAINT ck_financial_connections_scope
        CHECK (provider = 'CODEF' AND institution_type = 'BANK'),
    CONSTRAINT ck_financial_connections_organization
        CHECK (CHAR_LENGTH(BTRIM(organization_code)) > 0),
    CONSTRAINT ck_financial_connections_connected_id
        CHECK (CHAR_LENGTH(BTRIM(connected_id_ciphertext)) > 0),
    CONSTRAINT ck_financial_connections_registration_hash
        CHECK (registration_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_financial_connections_timestamps
        CHECK (updated_at >= created_at)
);

CREATE TABLE connected_accounts (
    account_ref UUID NOT NULL,
    connection_ref UUID NOT NULL,
    connection_version BIGINT NOT NULL,
    provider_account_ref_ciphertext TEXT NOT NULL,
    account_type VARCHAR(30) NOT NULL,
    product_code VARCHAR(30) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_connected_accounts PRIMARY KEY (account_ref),
    CONSTRAINT uk_connected_accounts_account_connection
        UNIQUE (account_ref, connection_ref),
    CONSTRAINT fk_connected_accounts_connection
        FOREIGN KEY (connection_ref)
            REFERENCES financial_connections (connection_ref)
            ON DELETE RESTRICT,
    CONSTRAINT ck_connected_accounts_connection_version
        CHECK (connection_version > 0),
    CONSTRAINT ck_connected_accounts_provider_reference
        CHECK (CHAR_LENGTH(BTRIM(provider_account_ref_ciphertext)) > 0),
    CONSTRAINT ck_connected_accounts_type
        CHECK (account_type IN ('DEPOSIT_TRUST', 'FOREIGN_CURRENCY')),
    CONSTRAINT ck_connected_accounts_product_code
        CHECK (CHAR_LENGTH(BTRIM(product_code)) > 0),
    CONSTRAINT ck_connected_accounts_currency
        CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_connected_accounts_timestamps
        CHECK (updated_at >= created_at)
);

CREATE INDEX idx_connected_accounts_active_connection
    ON connected_accounts (connection_ref, account_ref)
    WHERE active;

CREATE TABLE collection_executions (
    sync_job_id UUID NOT NULL,
    command_message_id UUID NOT NULL,
    account_ref UUID NOT NULL,
    connection_ref UUID NOT NULL,
    sync_version BIGINT NOT NULL,
    connection_version BIGINT NOT NULL,
    command_payload_hash VARCHAR(64) NOT NULL,
    from_date DATE NOT NULL,
    to_date DATE NOT NULL,
    deadline_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    status VARCHAR(30) NOT NULL,
    next_page_number INTEGER NOT NULL DEFAULT 1,
    next_chunk_index INTEGER NOT NULL DEFAULT 0,
    collected_transaction_count INTEGER NOT NULL DEFAULT 0,
    page_attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) WITHOUT TIME ZONE,
    lease_owner VARCHAR(100),
    lease_until TIMESTAMP(6) WITHOUT TIME ZONE,
    lease_version BIGINT NOT NULL DEFAULT 0,
    total_chunk_count INTEGER,
    total_transaction_count INTEGER,
    failure_code VARCHAR(64),
    safe_message VARCHAR(255),
    completed_at TIMESTAMP(6) WITHOUT TIME ZONE,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_collection_executions PRIMARY KEY (sync_job_id),
    CONSTRAINT uk_collection_executions_command UNIQUE (command_message_id),
    CONSTRAINT uk_collection_executions_job_account
        UNIQUE (sync_job_id, account_ref),
    CONSTRAINT uk_collection_executions_account_version
        UNIQUE (account_ref, sync_version),
    CONSTRAINT fk_collection_executions_account
        FOREIGN KEY (account_ref, connection_ref)
            REFERENCES connected_accounts (account_ref, connection_ref)
            ON DELETE RESTRICT,
    CONSTRAINT fk_collection_executions_connection
        FOREIGN KEY (connection_ref)
            REFERENCES financial_connections (connection_ref)
            ON DELETE RESTRICT,
    CONSTRAINT ck_collection_executions_sync_version
        CHECK (sync_version > 0),
    CONSTRAINT ck_collection_executions_connection_version
        CHECK (connection_version > 0),
    CONSTRAINT ck_collection_executions_command_hash
        CHECK (command_payload_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_collection_executions_date_range
        CHECK (from_date <= to_date AND to_date - from_date <= 30),
    CONSTRAINT ck_collection_executions_status
        CHECK (
            status IN (
                'QUEUED',
                'COLLECTING',
                'SUCCEEDED',
                'REAUTH_REQUIRED',
                'FAILED',
                'TIMED_OUT',
                'CANCELLED'
            )
        ),
    CONSTRAINT ck_collection_executions_progress
        CHECK (
            next_page_number > 0
            AND next_chunk_index >= 0
            AND collected_transaction_count >= 0
            AND page_attempt_count BETWEEN 0 AND 3
            AND lease_version >= 0
        ),
    CONSTRAINT ck_collection_executions_totals
        CHECK (
            (
                total_chunk_count IS NULL
                AND total_transaction_count IS NULL
            )
            OR (
                total_chunk_count IS NOT NULL
                AND total_transaction_count IS NOT NULL
                AND total_chunk_count >= 0
                AND total_transaction_count >= 0
            )
        ),
    CONSTRAINT ck_collection_executions_lease
        CHECK (
            (lease_owner IS NULL AND lease_until IS NULL)
            OR (
                status IN ('QUEUED', 'COLLECTING')
                AND CHAR_LENGTH(BTRIM(lease_owner)) > 0
                AND lease_until IS NOT NULL
            )
        ),
    CONSTRAINT ck_collection_executions_lifecycle
        CHECK (
            (
                status IN ('QUEUED', 'COLLECTING')
                AND next_attempt_at IS NOT NULL
                AND completed_at IS NULL
            )
            OR (
                status IN (
                    'SUCCEEDED',
                    'REAUTH_REQUIRED',
                    'FAILED',
                    'TIMED_OUT',
                    'CANCELLED'
                )
                AND next_attempt_at IS NULL
                AND lease_owner IS NULL
                AND lease_until IS NULL
                AND completed_at IS NOT NULL
            )
        ),
    CONSTRAINT ck_collection_executions_result
        CHECK (
            (
                status = 'SUCCEEDED'
                AND total_chunk_count IS NOT NULL
                AND total_transaction_count IS NOT NULL
                AND total_chunk_count = next_chunk_index
                AND total_transaction_count = collected_transaction_count
                AND failure_code IS NULL
                AND safe_message IS NULL
            )
            OR (
                status IN ('QUEUED', 'COLLECTING')
                AND total_chunk_count IS NULL
                AND total_transaction_count IS NULL
                AND failure_code IS NULL
                AND safe_message IS NULL
            )
            OR (
                status IN ('REAUTH_REQUIRED', 'FAILED', 'TIMED_OUT', 'CANCELLED')
                AND failure_code IS NOT NULL
                AND CHAR_LENGTH(BTRIM(failure_code)) > 0
                AND safe_message IS NOT NULL
                AND CHAR_LENGTH(BTRIM(safe_message)) > 0
            )
        ),
    CONSTRAINT ck_collection_executions_timestamps
        CHECK (
            updated_at >= created_at
            AND (completed_at IS NULL OR completed_at >= created_at)
        )
);

CREATE UNIQUE INDEX uk_collection_executions_active_account
    ON collection_executions (account_ref)
    WHERE status IN ('QUEUED', 'COLLECTING');

CREATE INDEX idx_collection_executions_ready
    ON collection_executions (next_attempt_at, deadline_at, sync_job_id)
    WHERE status IN ('QUEUED', 'COLLECTING');

CREATE INDEX idx_collection_executions_expired_lease
    ON collection_executions (lease_until, sync_job_id)
    WHERE lease_until IS NOT NULL;

CREATE TABLE finance_command_inbox (
    message_id UUID NOT NULL,
    sync_job_id UUID NOT NULL,
    account_ref UUID NOT NULL,
    message_type VARCHAR(64) NOT NULL,
    schema_version INTEGER NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    received_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    processed_at TIMESTAMP(6) WITHOUT TIME ZONE,
    CONSTRAINT pk_finance_command_inbox PRIMARY KEY (message_id),
    CONSTRAINT ck_finance_command_inbox_message_type
        CHECK (message_type = 'SyncAccountTransactions'),
    CONSTRAINT ck_finance_command_inbox_schema_version
        CHECK (schema_version = 1),
    CONSTRAINT ck_finance_command_inbox_payload_hash
        CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_finance_command_inbox_timestamps
        CHECK (processed_at IS NULL OR processed_at >= received_at)
);

CREATE INDEX idx_finance_command_inbox_job
    ON finance_command_inbox (sync_job_id, received_at, message_id);

CREATE INDEX idx_finance_command_inbox_processed
    ON finance_command_inbox (processed_at, message_id)
    WHERE processed_at IS NOT NULL;

CREATE TABLE finance_event_outbox (
    message_id UUID NOT NULL,
    sync_job_id UUID NOT NULL,
    account_ref UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) WITHOUT TIME ZONE,
    processing_started_at TIMESTAMP(6) WITHOUT TIME ZONE,
    completed_at TIMESTAMP(6) WITHOUT TIME ZONE,
    last_failure_code VARCHAR(64),
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_finance_event_outbox PRIMARY KEY (message_id),
    CONSTRAINT fk_finance_event_outbox_execution
        FOREIGN KEY (sync_job_id, account_ref)
            REFERENCES collection_executions (sync_job_id, account_ref)
            ON DELETE RESTRICT,
    CONSTRAINT ck_finance_event_outbox_event_type
        CHECK (
            event_type IN (
                'SyncAccepted',
                'TransactionsChunkCollected',
                'TransactionsCollectionCompleted',
                'TransactionsSyncFailed'
            )
        ),
    CONSTRAINT ck_finance_event_outbox_topic
        CHECK (topic = 'finance.sync.event.v1'),
    CONSTRAINT ck_finance_event_outbox_payload_hash
        CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_finance_event_outbox_payload
        CHECK (
            JSONB_TYPEOF(payload) = 'object'
            AND payload ->> 'messageId' = message_id::text
            AND payload ->> 'messageType' = event_type
            AND payload ->> 'schemaVersion' = '1'
            AND payload ->> 'syncJobId' = sync_job_id::text
            AND payload ->> 'accountRef' = account_ref::text
        ),
    CONSTRAINT ck_finance_event_outbox_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_finance_event_outbox_attempt_count
        CHECK (
            (status = 'PENDING' AND attempt_count = 0)
            OR (status <> 'PENDING' AND attempt_count > 0)
        ),
    CONSTRAINT ck_finance_event_outbox_lifecycle
        CHECK (
            (
                status IN ('PENDING', 'RETRY')
                AND next_attempt_at IS NOT NULL
                AND processing_started_at IS NULL
                AND completed_at IS NULL
            )
            OR (
                status = 'PROCESSING'
                AND next_attempt_at IS NULL
                AND processing_started_at IS NOT NULL
                AND completed_at IS NULL
            )
            OR (
                status IN ('PUBLISHED', 'FAILED')
                AND next_attempt_at IS NULL
                AND processing_started_at IS NULL
                AND completed_at IS NOT NULL
            )
        ),
    CONSTRAINT ck_finance_event_outbox_failure
        CHECK (
            (status IN ('PENDING', 'PUBLISHED') AND last_failure_code IS NULL)
            OR (status IN ('RETRY', 'FAILED') AND last_failure_code IS NOT NULL)
            OR status = 'PROCESSING'
        ),
    CONSTRAINT ck_finance_event_outbox_timestamps
        CHECK (updated_at >= created_at)
);

CREATE INDEX idx_finance_event_outbox_ready
    ON finance_event_outbox (next_attempt_at, created_at, message_id)
    WHERE status IN ('PENDING', 'RETRY');

CREATE INDEX idx_finance_event_outbox_processing
    ON finance_event_outbox (processing_started_at, message_id)
    WHERE status = 'PROCESSING';

CREATE INDEX idx_finance_event_outbox_completed
    ON finance_event_outbox (completed_at, message_id)
    WHERE status IN ('PUBLISHED', 'FAILED');
