-- What BITS did on its own: sync runs, account links and errors, for the dashboard. Names are snapshots taken when the
-- entry was written, so it stays readable after a member or role is gone. Kept for 30 days.
CREATE TABLE system_log_entry
(
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at              TIMESTAMPTZ  NOT NULL,
    kind                     VARCHAR(32)  NOT NULL,
    -- Sync runs only; the counts after the bot reported back.
    sync_trigger             VARCHAR(32),
    sync_status              VARCHAR(32),
    linked_count             INTEGER,
    member_count             INTEGER,
    stale_member_count       INTEGER,
    granted_count            INTEGER,
    removed_count            INTEGER,
    skipped_count            INTEGER,
    failed_count             INTEGER,
    reported_at              TIMESTAMPTZ,
    -- The member, WarEra account or role an account event or error is about.
    discord_user_id          VARCHAR(32),
    discord_name             VARCHAR(100),
    previous_discord_user_id VARCHAR(32),
    previous_discord_name    VARCHAR(100),
    warera_user_id           VARCHAR(64),
    warera_username          VARCHAR(255),
    role_id                  VARCHAR(32),
    role_name                VARCHAR(100),
    -- German text for errors.
    message                  TEXT
);

CREATE INDEX system_log_entry_occurred_at ON system_log_entry (occurred_at);
CREATE INDEX system_log_entry_kind ON system_log_entry (kind, id DESC);
CREATE INDEX system_log_entry_discord_user_id ON system_log_entry (discord_user_id);
CREATE INDEX system_log_entry_role_id ON system_log_entry (role_id);

-- One role change of a sync run as the bot reported it, or one member an error is about. Deleted with its entry.
CREATE TABLE system_log_item
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entry_id        BIGINT      NOT NULL REFERENCES system_log_entry (id) ON DELETE CASCADE,
    discord_user_id VARCHAR(32) NOT NULL,
    discord_name    VARCHAR(100),
    role_id         VARCHAR(32),
    role_name       VARCHAR(100),
    outcome         VARCHAR(16) NOT NULL,
    -- German text why a change was skipped or failed.
    reason          TEXT
);

CREATE INDEX system_log_item_entry_id ON system_log_item (entry_id);
CREATE INDEX system_log_item_discord_user_id ON system_log_item (discord_user_id);
CREATE INDEX system_log_item_role_id ON system_log_item (role_id);
