-- Who may see a reported text channel and whether the bot may create embassy threads there. Channels reported before
-- count as visible to everybody and as not suitable until the bot reports them again.
ALTER TABLE discord_channel
    ADD COLUMN everyone_can_view BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN threads_creatable BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE discord_channel_viewer_role
(
    channel_id VARCHAR(32) NOT NULL REFERENCES discord_channel (id) ON DELETE CASCADE,
    role_id    VARCHAR(32) NOT NULL,
    PRIMARY KEY (channel_id, role_id)
);

-- The channel the bot creates embassy threads in; NULL while none is set.
ALTER TABLE embassy_setting
    ADD COLUMN channel_id VARCHAR(32);

-- An embassy thread an admin asked the bot to create, until the bot reports it; one per country. The row stays with
-- the reason in German if creating it failed, until the admin tries again or assigns another thread.
CREATE TABLE embassy_thread_creation
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    country_id   VARCHAR(64)  NOT NULL UNIQUE,
    country_name VARCHAR(255) NOT NULL,
    thread_name  VARCHAR(100) NOT NULL,
    channel_id   VARCHAR(32)  NOT NULL,
    requested_at TIMESTAMPTZ  NOT NULL,
    failure      VARCHAR(255)
);
