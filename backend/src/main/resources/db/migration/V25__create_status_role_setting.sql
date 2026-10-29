-- The roles for verified and unverified members; a single row, written once an admin sets them. Until then, and for a
-- role that is NULL, BITS gives no status role.
CREATE TABLE status_role_setting
(
    id                 SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    verified_role_id   VARCHAR(32),
    unverified_role_id VARCHAR(32),
    updated_at         TIMESTAMPTZ NOT NULL,
    CHECK (verified_role_id <> unverified_role_id)
);
