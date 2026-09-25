CREATE TABLE account (
    id uuid PRIMARY KEY,
    email_normalized varchar(320) NOT NULL UNIQUE,
    password_hash varchar(255) NOT NULL,
    verified_at timestamptz,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE email_verification (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES account(id),
    token_hash varchar(64) NOT NULL UNIQUE,
    issued_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    superseded_at timestamptz,
    CONSTRAINT ck_email_verification_expiry CHECK (expires_at = issued_at + interval '24 hours')
);

CREATE UNIQUE INDEX uq_email_verification_unsuperseded
    ON email_verification(account_id)
    WHERE consumed_at IS NULL AND superseded_at IS NULL;
CREATE INDEX ix_email_verification_account ON email_verification(account_id, issued_at DESC);

CREATE TABLE auth_session (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES account(id),
    access_token_hash varchar(64) NOT NULL UNIQUE,
    refresh_token_hash varchar(64) NOT NULL UNIQUE,
    previous_refresh_token_hash varchar(64),
    access_expires_at timestamptz NOT NULL,
    refresh_expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL,
    rotated_at timestamptz,
    version bigint NOT NULL DEFAULT 0
);
CREATE INDEX ix_auth_session_account ON auth_session(account_id);
CREATE INDEX ix_auth_session_refresh ON auth_session(refresh_token_hash);
