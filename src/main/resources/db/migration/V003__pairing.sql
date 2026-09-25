CREATE TABLE invitation (
    id uuid PRIMARY KEY,
    inviter_account_id uuid NOT NULL REFERENCES account(id),
    target_email_normalized varchar(320) NOT NULL,
    time_zone varchar(64) NOT NULL,
    status varchar(24) NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    delivered_at timestamptz,
    terminal_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_invitation_status CHECK (status IN ('PENDING_DELIVERY','PENDING','DELIVERY_FAILED','ACCEPTED','CANCELLED','EXPIRED','INVALIDATED')),
    CONSTRAINT ck_invitation_expiry CHECK (expires_at = created_at + interval '7 days')
);
CREATE UNIQUE INDEX uq_invitation_active_target ON invitation(inviter_account_id,target_email_normalized)
    WHERE status IN ('PENDING_DELIVERY','PENDING','DELIVERY_FAILED');
CREATE INDEX ix_invitation_target_status ON invitation(target_email_normalized,status,expires_at);
CREATE INDEX ix_invitation_inviter ON invitation(inviter_account_id,created_at DESC);

CREATE TABLE couple_pair (
    id uuid PRIMARY KEY,
    time_zone varchar(64) NOT NULL,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0
);
CREATE TABLE pair_member (
    pair_id uuid NOT NULL REFERENCES couple_pair(id),
    account_id uuid NOT NULL REFERENCES account(id),
    joined_at timestamptz NOT NULL,
    PRIMARY KEY(pair_id,account_id),
    UNIQUE (account_id)
);
