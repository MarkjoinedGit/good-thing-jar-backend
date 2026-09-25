CREATE TABLE outbox_message (
    id uuid PRIMARY KEY,
    message_type varchar(64) NOT NULL,
    aggregate_id uuid,
    recipient varchar(320) NOT NULL,
    payload text NOT NULL,
    encrypted_secret text,
    status varchar(24) NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    available_at timestamptz NOT NULL,
    claimed_at timestamptz,
    processed_at timestamptz,
    last_error_code varchar(64),
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING','CLAIMED','DELIVERED','FAILED','SKIPPED'))
);
CREATE INDEX ix_outbox_claim ON outbox_message(status, available_at, created_at);

CREATE TABLE security_audit_event (
    id uuid PRIMARY KEY,
    event_type varchar(80) NOT NULL,
    actor_scope varchar(64),
    outcome varchar(32) NOT NULL,
    correlation_id varchar(100),
    occurred_at timestamptz NOT NULL
);
CREATE INDEX ix_security_audit_occurred ON security_audit_event(occurred_at DESC);

CREATE TABLE abuse_throttle_bucket (
    operation varchar(48) NOT NULL,
    scope_hash varchar(64) NOT NULL,
    window_started_at timestamptz NOT NULL,
    request_count integer NOT NULL,
    expires_at timestamptz NOT NULL,
    PRIMARY KEY (operation, scope_hash, window_started_at)
);
CREATE INDEX ix_throttle_cleanup ON abuse_throttle_bucket(expires_at);
