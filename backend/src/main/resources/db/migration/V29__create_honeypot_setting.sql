-- Whether the bot may watch a channel as a honeypot: post its warning, delete messages and kick.
ALTER TABLE discord_channel
    ADD COLUMN honeypot_usable BOOLEAN NOT NULL DEFAULT FALSE;

-- The honeypot channel and the bot's warning in it; a single row, written once there is something to keep.
CREATE TABLE honeypot_setting
(
    id                 SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    -- NULL while no honeypot is set.
    channel_id         VARCHAR(32),
    -- Where the warning is; differs from channel_id until the bot has moved it.
    warning_channel_id VARCHAR(32),
    warning_message_id VARCHAR(32),
    kicks_paused       BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at         TIMESTAMPTZ NOT NULL
);
