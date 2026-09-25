CREATE TABLE unlock_proposal (
    id uuid PRIMARY KEY,
    jar_id uuid NOT NULL REFERENCES shared_jar(id),
    proposed_by_account_id uuid NOT NULL REFERENCES account(id),
    proposed_unlock_at timestamptz NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL,
    resolved_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_unlock_proposal_status CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED','EXPIRED'))
);
CREATE UNIQUE INDEX uq_unlock_proposal_pending ON unlock_proposal(jar_id) WHERE status='PENDING';
CREATE INDEX ix_unlock_proposal_jar_created ON unlock_proposal(jar_id,created_at DESC);
