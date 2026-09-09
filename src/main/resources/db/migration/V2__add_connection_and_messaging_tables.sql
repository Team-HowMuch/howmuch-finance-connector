CREATE TABLE financial_connections (
    connection_ref UUID PRIMARY KEY,
    connection_version INTEGER NOT NULL,
    provider VARCHAR(30) NOT NULL,
    institution_type VARCHAR(30) NOT NULL,
    organization_code VARCHAR(50) NOT NULL,
    connected_id VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE connected_accounts (
    account_ref UUID PRIMARY KEY,
    connection_ref UUID NOT NULL REFERENCES financial_connections(connection_ref),
    provider_account_ref VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX connected_accounts_idx_connection_ref ON connected_accounts(connection_ref);

CREATE TABLE collection_executions (
    sync_job_id UUID PRIMARY KEY,
    command_message_id UUID NOT NULL,
    account_ref UUID NOT NULL,
    connection_ref UUID NOT NULL,
    sync_version BIGINT NOT NULL,
    connection_version BIGINT NOT NULL,
    from_date DATE NOT NULL,
    to_date DATE NOT NULL,
    deadline_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(30) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    next_chunk_index INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    page_attempt_count INTEGER NOT NULL DEFAULT 0,
    lease_until TIMESTAMPTZ,
    lease_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX collection_executions_idx_ready ON collection_executions(status, next_attempt_at);

CREATE TABLE processed_messages (
    id BIGSERIAL PRIMARY KEY,
    consumer_name VARCHAR(100) NOT NULL,
    message_id UUID NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT processed_messages_uq_consumer_message UNIQUE (consumer_name, message_id)
);

CREATE TABLE outbox_events (
    message_id UUID PRIMARY KEY,
    sync_job_id UUID NOT NULL,
    topic VARCHAR(200) NOT NULL,
    message_key VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ
);

CREATE INDEX outbox_events_idx_pending ON outbox_events(status, next_attempt_at, created_at);
