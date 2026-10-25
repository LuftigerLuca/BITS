-- Who may see the channel of a reported thread, since Discord adds only those to a private thread. Threads reported
-- before count as visible to everybody until the bot reports them again.
ALTER TABLE discord_thread
    ADD COLUMN everyone_can_view BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE discord_thread_viewer_role
(
    thread_id VARCHAR(32) NOT NULL REFERENCES discord_thread (id) ON DELETE CASCADE,
    role_id   VARCHAR(32) NOT NULL,
    PRIMARY KEY (thread_id, role_id)
);
