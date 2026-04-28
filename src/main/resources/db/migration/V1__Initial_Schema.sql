--- 1. Extensions, Types
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE TYPE couples_status AS ENUM ('ACTIVE', 'SUSPENDED', 'DELETED');
CREATE TYPE jars_status AS ENUM ('WRITABLE', 'ARCHIVED', 'DELETED');
CREATE TYPE entries_status AS ENUM ('PENDING_SYNC', 'SYNCED', 'UNLOCKED', 'DELETED');
CREATE TYPE pairing_codes_status AS ENUM ('ACTIVE', 'CLAIMED', 'EXPIRED', 'REVOKED');
CREATE TYPE unlock_date_proposal_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED');
CREATE TYPE frequency AS ENUM ('DAILY', 'WEEKLY', 'MONTHLY', 'NONE');

--- 2. Tables
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    profile_picture_url VARCHAR(500),
    last_login TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE couples (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user1_id UUID NOT NULL,
    user2_id UUID NOT NULL,
    status couples_status NOT NULL,
    paired_at TIMESTAMP WITH TIME ZONE NOT NULL,
    suspended_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    -- Constraint: Prevent self-pairing
    CONSTRAINT chk_user_ids_different CHECK ( user1_id <> user2_id ),
    FOREIGN KEY (user1_id) REFERENCES users(id) ON DELETE RESTRICT,
    FOREIGN KEY (user2_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE TABLE jars (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    couple_id UUID NOT NULL,
    status jars_status NOT NULL,
    unlocks_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    entry_count INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    -- Constraint :`unlocksAt` must be in the future when `status = WRITABLE`
    CONSTRAINT chk_unlocks_future_when_writable
        CHECK (
            status <> 'WRITABLE'
            OR unlocks_at > CURRENT_TIMESTAMP
            ),

    -- Constraint : `archivedAt` is NOT NULL only when `status = ARCHIVED`
    CONSTRAINT chk_archived_not_null_when_archived
        CHECK (
            (status = 'ARCHIVED' AND archived_at IS NOT NULL)
                OR (status <> 'ARCHIVED' AND archived_at IS NULL)
            ),

    -- Constraint : 'entryCount' >= 0
    CONSTRAINT chk_entry_count_nonnegative CHECK ( entry_count >= 0 ),

    FOREIGN KEY (couple_id) REFERENCES couples(id)
);

CREATE TABLE entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    jar_id UUID NOT NULL,
    author_id UUID NOT NULL,
    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    status entries_status NOT NULL,
    synced_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_generated_id UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    -- Constraint: content cannot be empty
    CONSTRAINT chk_content_not_empty CHECK (char_length(content) BETWEEN 1 AND 10000),

    FOREIGN KEY (jar_id) REFERENCES jars(id) ON DELETE RESTRICT ,
    FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE TABLE pairing_codes(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    generated_by_user_id UUID NOT NULL,
    claimed_by_user_id UUID,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE,
    status pairing_codes_status NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraint: Prevent self-pairing
    CONSTRAINT chk_user_ids_different CHECK ( generated_by_user_id <> claimed_by_user_id ),

    FOREIGN KEY (generated_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    FOREIGN KEY (claimed_by_user_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE TABLE unlock_date_proposals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    jar_id UUID NOT NULL,
    proposed_by UUID NOT NULL,
    approved_by UUID,
    new_unlocks_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status unlock_date_proposal_status NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    rejection_reason VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP WITH TIME ZONE,

    -- Constraint: `newUnlocksAt` must be in the future
    CONSTRAINT chk_new_unlocks_at_future CHECK ( new_unlocks_at > CURRENT_TIMESTAMP ),

    -- Constraint: `proposedBy` != `approvedBy` (different users)
    CONSTRAINT chk_user_ids_different CHECK ( proposed_by <> approved_by ),

    FOREIGN KEY (jar_id) REFERENCES jars(id) ON DELETE RESTRICT ,
    FOREIGN KEY (proposed_by) REFERENCES users(id) ON DELETE RESTRICT,
    FOREIGN KEY (approved_by) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE TABLE timezone_ref (
    name TEXT PRIMARY KEY
);

INSERT INTO timezone_ref (name) SELECT name FROM pg_timezone_names;

CREATE TABLE notification_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE,
    frequency frequency NOT NULL,
    local_time_zone VARCHAR(50) NOT NULL,
    notification_time TIME NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ,
    FOREIGN KEY (local_time_zone) REFERENCES timezone_ref(name)
);

--- 3. Indexes
CREATE INDEX idx_users_deleted_at ON users(deleted_at);
CREATE INDEX idx_users_created_at ON users(created_at);

CREATE INDEX idx_couples_user1_id_status ON couples(user1_id, status);
CREATE INDEX idx_couples_user2_id_status ON couples(user2_id, status);
CREATE UNIQUE INDEX uniq_couples_pair
    ON couples (
                LEAST(user1_id, user2_id),
                GREATEST(user1_id, user2_id)
        )
;

CREATE INDEX idx_jars_couple_status ON jars(couple_id, status);
CREATE INDEX idx_jars_unlocks_at ON jars(unlocks_at);
CREATE INDEX idx_jars_couple_status_unlocks_at ON jars(couple_id, status, unlocks_at);
CREATE UNIQUE INDEX uq_one_writable_jar_per_couple ON jars(couple_id) WHERE status = 'WRITABLE';

CREATE INDEX idx_entries_jar_created_desc ON entries(jar_id, created_at DESC);
CREATE INDEX idx_entries_author_id ON entries(author_id);
CREATE INDEX idx_entries_status ON entries(status);
CREATE INDEX idx_entries_jar_status ON entries(jar_id, status);
CREATE UNIQUE INDEX uq_author_request on entries(author_id, client_generated_id) WHERE client_generated_id IS NOT NULL;

CREATE INDEX idx_pairing_codes_generated_by_user_expires ON pairing_codes(generated_by_user_id, expires_at);
CREATE INDEX idx_pairing_codes_status_expires_at ON pairing_codes(status, expires_at);
CREATE UNIQUE INDEX unique_active_user_status ON pairing_codes(generated_by_user_id, status) WHERE status = 'ACTIVE';

CREATE INDEX idx_unlock_date_proposals_jar_status ON unlock_date_proposals(jar_id, status);
CREATE INDEX idx_unlock_date_proposals_propose_by ON unlock_date_proposals(proposed_by);
CREATE INDEX idx_unlock_date_proposals_expires_at ON unlock_date_proposals(expires_at);
CREATE UNIQUE INDEX uq_one_pending_proposal_per_jar ON unlock_date_proposals(jar_id) WHERE status = 'PENDING';

CREATE INDEX idx_notification_preferences_user_enabled ON notification_preferences(user_id, enabled);
CREATE INDEX idx_notification_preferences_frequency_local_time_zone ON notification_preferences(frequency, local_time_zone);

--- 4. Function
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE OR REPLACE FUNCTION set_expires_at()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.expires_at := NEW.created_at + INTERVAL '24 hours';
END IF;

    IF TG_OP = 'UPDATE' THEN
        NEW.expires_at := NEW.created_at + INTERVAL '24 hours';
END IF;

RETURN NEW;
END;
$$ LANGUAGE plpgsql;

--- 5. Triggers
CREATE TRIGGER set_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER set_couples_updated_at
    BEFORE UPDATE ON couples
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER set_jars_updated_at
    BEFORE UPDATE ON jars
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER set_entries_updated_at
    BEFORE UPDATE ON entries
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER set_pairing_codes_expires_at
    BEFORE INSERT OR UPDATE ON pairing_codes
    FOR EACH ROW
    EXECUTE FUNCTION set_expires_at();

CREATE TRIGGER set_notification_preferences_updated_at
    BEFORE UPDATE ON notification_preferences
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();