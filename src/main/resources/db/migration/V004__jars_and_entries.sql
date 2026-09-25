CREATE TABLE shared_jar (
    id uuid PRIMARY KEY,
    pair_id uuid NOT NULL REFERENCES couple_pair(id),
    sequence_number integer NOT NULL,
    current boolean NOT NULL,
    time_zone varchar(64) NOT NULL,
    effective_unlock_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    UNIQUE(pair_id,sequence_number)
);
CREATE UNIQUE INDEX uq_jar_current_pair ON shared_jar(pair_id) WHERE current;
CREATE INDEX ix_jar_pair_history ON shared_jar(pair_id,sequence_number DESC);

CREATE TABLE jar_entry (
    id uuid PRIMARY KEY,
    jar_id uuid NOT NULL REFERENCES shared_jar(id),
    author_account_id uuid NOT NULL REFERENCES account(id),
    text varchar(5000) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT ck_entry_text CHECK (char_length(text) BETWEEN 1 AND 5000)
);
CREATE INDEX ix_entry_keyset ON jar_entry(jar_id,created_at,id);
