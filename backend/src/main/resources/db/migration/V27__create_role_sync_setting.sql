-- Settings of the role sync; a single row, written once an admin changes one. Until then, the defaults hold.
CREATE TABLE role_sync_setting
(
    id                    SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    removal_limit_enabled BOOLEAN     NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL
);
